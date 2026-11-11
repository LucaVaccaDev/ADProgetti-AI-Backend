package it.adprogetti.zio.edge.docs;

import java.io.ByteArrayOutputStream;

/** Builds a minimal SignedData envelope (.p7m) around a document, in DER or in BER with indefinite lengths. */
final class TestP7m {

    private TestP7m() {
    }

    static byte[] tlv(int tag, byte[]... parts) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            body.writeBytes(p);
        }
        byte[] b = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (b.length < 128) {
            out.write(b.length);
        } else {
            out.write(0x84);
            out.write(b.length >>> 24);
            out.write(b.length >>> 16);
            out.write(b.length >>> 8);
            out.write(b.length);
        }
        out.writeBytes(b);
        return out.toByteArray();
    }

    static byte[] indefinite(int tag, byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        out.write(0x80);
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        out.write(0);
        out.write(0);
        return out.toByteArray();
    }

    static final byte[] OID_SIGNED_DATA = tlv(0x06, new byte[] { 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x07, 0x02 });
    static final byte[] OID_DATA = tlv(0x06, new byte[] { 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x07, 0x01 });

    /** Definite lengths, content as one OCTET STRING, with a fake certificate and signer info around it. */
    static byte[] der(byte[] content) {
        byte[] signedData = tlv(0x30, tlv(0x02, new byte[] { 1 }), tlv(0x31), tlv(0x30, OID_DATA, tlv(0xA0, tlv(0x04, content))),
                tlv(0xA0, tlv(0x30, tlv(0x04, new byte[] { 9, 9 }))), tlv(0x31));
        return tlv(0x30, OID_SIGNED_DATA, tlv(0xA0, signedData));
    }

    /** What many signing programs write: indefinite lengths and the content split in chunks. */
    static byte[] ber(byte[] content) {
        int half = content.length / 2;
        byte[] first = java.util.Arrays.copyOfRange(content, 0, half);
        byte[] second = java.util.Arrays.copyOfRange(content, half, content.length);
        byte[] octets = indefinite(0x24, tlv(0x04, first), tlv(0x04, second));
        byte[] signedData = indefinite(0x30, tlv(0x02, new byte[] { 1 }), tlv(0x31),
                indefinite(0x30, OID_DATA, indefinite(0xA0, octets)), tlv(0x31));
        return indefinite(0x30, OID_SIGNED_DATA, indefinite(0xA0, signedData));
    }
}
