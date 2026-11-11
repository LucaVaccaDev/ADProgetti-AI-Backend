package it.adprogetti.zio.edge.docs;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Takes the document out of a digitally signed envelope (.p7m, CAdES / PKCS#7 SignedData). Only the enclosed content is
 * read: signatures and certificates are NOT validated here, because the document is read for its text and then deleted,
 * never relied on as proof (an official verification of signatures is a separate step of the tender workflow).
 * Written on the JDK (a small BER reader) so the Edge needs no extra library. The input is untrusted: every length is
 * checked against the buffer, nesting is limited, and anything unexpected gives an empty result.
 */
final class P7mUnwrapper {

    private static final int MAX_DEPTH = 8;
    private static final int MAX_NODES = 50_000;

    private record Node(int tag, int start, int end, int next) {
        boolean constructed() {
            return (tag & 0x20) != 0;
        }
    }

    private P7mUnwrapper() {
    }

    /** The enclosed content of a SignedData envelope, unwrapping up to three nested envelopes; empty if it is not one. */
    static Optional<byte[]> unwrap(byte[] data) {
        byte[] current = data;
        for (int level = 0; level < 3; level++) {
            Optional<byte[]> inner = unwrapOnce(current);
            if (inner.isEmpty()) {
                return level == 0 ? Optional.empty() : Optional.of(current);
            }
            current = inner.get();
            if (isPdf(current)) {
                return Optional.of(current);
            }
        }
        return Optional.of(current);
    }

    static boolean isPdf(byte[] d) {
        return d.length > 5 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F' && d[4] == '-';
    }

    private static Optional<byte[]> unwrapOnce(byte[] d) {
        try {
            int[] counter = { 0 };
            Node contentInfo = node(d, 0, d.length);
            if (contentInfo == null || contentInfo.tag != 0x30) {
                return Optional.empty();
            }
            List<Node> ci = children(d, contentInfo, 0, counter);
            if (ci.size() < 2 || ci.get(0).tag != 0x06 || !isSignedDataOid(d, ci.get(0)) || ci.get(1).tag != 0xA0) {
                return Optional.empty();
            }
            List<Node> wrapper = children(d, ci.get(1), 1, counter);
            if (wrapper.isEmpty() || wrapper.get(0).tag != 0x30) {
                return Optional.empty();
            }
            for (Node n : children(d, wrapper.get(0), 2, counter)) {
                if (n.tag != 0x30) {
                    continue; // version, digest algorithms, certificates... only the encapsulated content info is a plain SEQUENCE
                }
                List<Node> encap = children(d, n, 3, counter);
                if (encap.size() >= 2 && encap.get(0).tag == 0x06 && encap.get(1).tag == 0xA0) {
                    List<Node> inner = children(d, encap.get(1), 4, counter);
                    if (!inner.isEmpty() && (inner.get(0).tag == 0x04 || inner.get(0).tag == 0x24)) {
                        return Optional.of(octets(d, inner.get(0), counter));
                    }
                }
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** 1.2.840.113549.1.7.2 */
    private static boolean isSignedDataOid(byte[] d, Node oid) {
        byte[] expected = { 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x07, 0x02 };
        if (oid.end - oid.start != expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (d[oid.start + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] octets(byte[] d, Node n, int[] counter) {
        if (!n.constructed()) {
            return java.util.Arrays.copyOfRange(d, n.start, n.end);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Node c : children(d, n, 5, counter)) {
            if (c.tag == 0x04 || c.tag == 0x24) {
                byte[] part = octets(d, c, counter);
                out.write(part, 0, part.length);
            }
        }
        return out.toByteArray();
    }

    private static List<Node> children(byte[] d, Node parent, int depth, int[] counter) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("too deep");
        }
        List<Node> out = new ArrayList<>();
        int pos = parent.start;
        while (pos < parent.end) {
            if (++counter[0] > MAX_NODES) {
                throw new IllegalStateException("too many nodes");
            }
            if (pos + 1 < parent.end && d[pos] == 0 && d[pos + 1] == 0) {
                break; // end-of-contents of an indefinite length
            }
            Node n = node(d, pos, parent.end);
            if (n == null) {
                throw new IllegalStateException("bad length");
            }
            out.add(n);
            pos = n.next;
        }
        return out;
    }

    /** Reads the header at {@code pos}; null when it does not fit in the buffer. Indefinite lengths are measured. */
    private static Node node(byte[] d, int pos, int limit) {
        if (pos + 2 > limit) {
            return null;
        }
        int tag = d[pos] & 0xFF;
        if ((tag & 0x1F) == 0x1F) {
            return null; // multi-byte tags do not occur in the structures read here
        }
        int len = d[pos + 1] & 0xFF;
        int cs = pos + 2;
        if (len == 0x80) {
            if ((tag & 0x20) == 0) {
                return null;
            }
            int p = cs;
            int guard = 0;
            while (true) {
                if (++guard > MAX_NODES || p + 2 > limit) {
                    return null;
                }
                if (d[p] == 0 && d[p + 1] == 0) {
                    return new Node(tag, cs, p, p + 2);
                }
                Node child = node(d, p, limit);
                if (child == null) {
                    return null;
                }
                p = child.next;
            }
        }
        if (len > 0x80) {
            int n = len & 0x7F;
            if (n > 4 || cs + n > limit) {
                return null;
            }
            long l = 0;
            for (int i = 0; i < n; i++) {
                l = (l << 8) | (d[cs + i] & 0xFF);
            }
            if (l > Integer.MAX_VALUE) {
                return null;
            }
            len = (int) l;
            cs += n;
        }
        long end = (long) cs + len;
        if (end > limit) {
            return null;
        }
        return new Node(tag, cs, (int) end, (int) end);
    }
}
