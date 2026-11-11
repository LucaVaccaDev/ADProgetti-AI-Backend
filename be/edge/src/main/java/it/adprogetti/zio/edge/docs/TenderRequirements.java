package it.adprogetti.zio.edge.docs;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the studio needs to know from a tender's documents (capitolato E.2). Every value carries the page and the words
 * it was read from; {@code verificata} is set by CODE, never by the AI, when those words really are on that page. A
 * value that cannot be found in the text stays in the result marked as not verified: it is never dropped silently and
 * never trusted. Nothing here decides anything: it is information for the tender manager.
 */
public record TenderRequirements(Finding<Double> importoBaseGara, Finding<String> criterio, Finding<String> scadenzaOfferte,
        List<ServiceReq> servizi, List<TurnoverReq> fatturato, List<StaffReq> personale, List<DocReq> documenti,
        List<String> incertezze) {

    public static final Set<String> CRITERI = Set.of("OEPV", "PREZZO_PIU_BASSO", "ALTRO");

    /** A single value with its source. {@code verificata} false means the quote was not found in the text. */
    public record Finding<T>(T valore, Integer pagina, String citazione, boolean verificata) {
    }

    /** Past services required, per work id of DM 17/06/2016. Amounts in euro. */
    public record ServiceReq(String idOpera, Double importoEuro, Integer numeroServizi, Integer anni, String descrizione,
            Integer pagina, String citazione, boolean verificata) {
    }

    public record TurnoverReq(String descrizione, Double importoEuro, Integer anni, Integer pagina, String citazione,
            boolean verificata) {
    }

    public record StaffReq(String figura, Integer pagina, String citazione, boolean verificata) {
    }

    public record DocReq(String descrizione, Integer pagina, String citazione, boolean verificata) {
    }

    public TenderRequirements {
        servizi = servizi == null ? List.of() : List.copyOf(servizi);
        fatturato = fatturato == null ? List.of() : List.copyOf(fatturato);
        personale = personale == null ? List.of() : List.copyOf(personale);
        documenti = documenti == null ? List.of() : List.copyOf(documenti);
        incertezze = incertezze == null ? List.of() : List.copyOf(incertezze);
    }

    public static TenderRequirements empty() {
        return new TenderRequirements(null, null, null, null, null, null, null, null);
    }

    // --- what the model returns (one window of the document), before grounding -----------------------------------

    /** Raw model answer for one window. Unknown fields are refused by the strict parser. */
    public record Raw(RawValue<Double> importoBaseGara, RawValue<String> criterio, RawValue<String> scadenzaOfferte,
            List<RawService> servizi, List<RawTurnover> fatturato, List<RawStaff> personale, List<RawDoc> documenti,
            List<String> incertezze) {
    }

    public record RawValue<T>(T valore, Integer pagina, String citazione) {
    }

    public record RawService(String idOpera, Double importoEuro, Integer numeroServizi, Integer anni, String descrizione,
            Integer pagina, String citazione) {
    }

    public record RawTurnover(String descrizione, Double importoEuro, Integer anni, Integer pagina, String citazione) {
    }

    public record RawStaff(String figura, Integer pagina, String citazione) {
    }

    public record RawDoc(String descrizione, Integer pagina, String citazione) {
    }

    /** The JSON shape asked of the model; it goes in the instructions, the parser is the real check. */
    public static final String SHAPE = """
            {"importoBaseGara":{"valore":number|null,"pagina":int,"citazione":"parole esatte del testo"},
             "criterio":{"valore":"OEPV|PREZZO_PIU_BASSO|ALTRO","pagina":int,"citazione":"..."},
             "scadenzaOfferte":{"valore":"yyyy-MM-dd","pagina":int,"citazione":"..."},
             "servizi":[{"idOpera":"E.08","importoEuro":number|null,"numeroServizi":int|null,"anni":int|null,"descrizione":"...","pagina":int,"citazione":"..."}],
             "fatturato":[{"descrizione":"...","importoEuro":number|null,"anni":int|null,"pagina":int,"citazione":"..."}],
             "personale":[{"figura":"...","pagina":int,"citazione":"..."}],
             "documenti":[{"descrizione":"...","pagina":int,"citazione":"..."}],
             "incertezze":["..."]}""";

    // --- reading the model's answer, item by item --------------------------------------------------------------

    private static final JsonMapper LENIENT = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    /** Why a whole answer could not be read: a short code, never the model's words. */
    public static class UnreadableAnswer extends RuntimeException {
        public UnreadableAnswer(String code) {
            super(code);
        }
    }

    /**
     * Reads the answer of one window one item at a time: a malformed item is dropped and reported in the notes, it does
     * not take the others down with it. Nothing is repaired or guessed: a number written as text ("1.200.000,00") is NOT
     * converted, the item is dropped. Unknown extra fields are ignored. An answer that is not a JSON object is refused
     * with a code (NO_JSON, BAD_JSON, NOT_AN_OBJECT).
     */
    public static Raw parseLenient(String json) {
        if (json == null || json.indexOf('{') < 0) {
            throw new UnreadableAnswer("NO_JSON");
        }
        JsonNode root;
        try {
            root = LENIENT.readTree(json);
        } catch (JacksonException e) {
            throw new UnreadableAnswer("BAD_JSON"); // typically an answer cut short at the token limit
        }
        if (root == null || !root.isObject()) {
            throw new UnreadableAnswer("NOT_AN_OBJECT");
        }
        List<String> notes = new ArrayList<>();
        if (root.get("incertezze") != null && root.get("incertezze").isArray()) {
            root.get("incertezze").forEach(n -> {
                if (n.isString()) {
                    notes.add(n.asString());
                }
            });
        }
        RawValue<Double> amount = number(root.get("importoBaseGara"), notes, "IMPORTO_BASE_GARA");
        RawValue<String> criterio = text(root.get("criterio"), notes, "CRITERIO");
        RawValue<String> deadline = text(root.get("scadenzaOfferte"), notes, "SCADENZA");
        return new Raw(amount, criterio, deadline,
                items(root.get("servizi"), RawService.class, notes, "SERVIZIO_NON_VALIDO"),
                items(root.get("fatturato"), RawTurnover.class, notes, "FATTURATO_NON_VALIDO"),
                items(root.get("personale"), RawStaff.class, notes, "PERSONALE_NON_VALIDO"),
                items(root.get("documenti"), RawDoc.class, notes, "DOCUMENTO_NON_VALIDO"), notes);
    }

    private static RawValue<Double> number(JsonNode n, List<String> notes, String code) {
        if (n == null || n.isNull() || !n.isObject() || n.get("valore") == null || n.get("valore").isNull()) {
            return null;
        }
        if (!n.get("valore").isNumber()) {
            notes.add(code + "_NON_NUMERICO");
            return null;
        }
        return new RawValue<>(n.get("valore").asDouble(), intOrNull(n.get("pagina")), strOrNull(n.get("citazione")));
    }

    private static RawValue<String> text(JsonNode n, List<String> notes, String code) {
        if (n == null || n.isNull() || !n.isObject() || n.get("valore") == null || !n.get("valore").isString()) {
            return null;
        }
        return new RawValue<>(n.get("valore").asString(), intOrNull(n.get("pagina")), strOrNull(n.get("citazione")));
    }

    private static <T> List<T> items(JsonNode arr, Class<T> type, List<String> notes, String code) {
        List<T> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode el : arr) {
            try {
                out.add(LENIENT.treeToValue(el, type));
            } catch (JacksonException | IllegalArgumentException e) {
                notes.add(code);
            }
        }
        return out;
    }

    private static Integer intOrNull(JsonNode n) {
        return n != null && n.isIntegralNumber() ? n.asInt() : null;
    }

    private static String strOrNull(JsonNode n) {
        return n != null && n.isString() ? n.asString() : null;
    }

    // --- grounding ------------------------------------------------------------------------------------------------

    /** Pages of the document by number, for checking quotes. */
    public static final class Pages {
        private final Map<Integer, String> normalized = new LinkedHashMap<>();

        public Pages(Map<Integer, String> text) {
            text.forEach((n, t) -> normalized.put(n, normalise(t)));
        }

        /** True when the quote (at least 12 characters once normalised) appears on that page. */
        boolean contains(Integer page, String quote) {
            String q = normalise(quote);
            return page != null && q.length() >= 12 && normalized.getOrDefault(page, "").contains(q);
        }

        /** The page where the quote is, if it is on exactly one page; otherwise null. */
        Integer find(String quote) {
            String q = normalise(quote);
            if (q.length() < 12) {
                return null;
            }
            Integer found = null;
            for (var e : normalized.entrySet()) {
                if (e.getValue().contains(q)) {
                    if (found != null) {
                        return null;
                    }
                    found = e.getKey();
                }
            }
            return found;
        }
    }

    static String normalise(String s) {
        if (s == null) {
            return "";
        }
        String d = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return d.replaceAll("[^a-z0-9]+", " ").strip();
    }

    /**
     * Checks one window of model output against the text: ranges, dates, enumerations, and above all the quotes.
     * A quote found on another page moves the page; a quote found nowhere leaves the value unverified.
     */
    public static TenderRequirements ground(Raw raw, Pages pages) {
        List<String> notes = new ArrayList<>(raw.incertezze() == null ? List.of() : raw.incertezze().stream()
                .filter(Objects::nonNull).map(s -> cut(s, 300)).limit(20).toList());
        Finding<Double> amount = null;
        if (raw.importoBaseGara() != null && blank(raw.importoBaseGara().citazione())) {
            notes.add("IMPORTO_BASE_GARA_SENZA_CITAZIONE");
        } else if (raw.importoBaseGara() != null && validAmount(raw.importoBaseGara().valore())) {
            amount = finding(raw.importoBaseGara().valore(), raw.importoBaseGara().pagina(), raw.importoBaseGara().citazione(), pages);
        }
        Finding<String> criterio = null;
        if (raw.criterio() != null && blank(raw.criterio().citazione())) {
            notes.add("CRITERIO_SENZA_CITAZIONE"); // a value with no words behind it is not a finding
        } else if (raw.criterio() != null && raw.criterio().valore() != null) {
            String c = raw.criterio().valore().strip().toUpperCase(Locale.ROOT);
            if (CRITERI.contains(c)) {
                criterio = finding(c, raw.criterio().pagina(), raw.criterio().citazione(), pages);
            } else {
                notes.add("CRITERIO_NON_RICONOSCIUTO");
            }
        }
        Finding<String> deadline = null;
        if (raw.scadenzaOfferte() != null && blank(raw.scadenzaOfferte().citazione())) {
            notes.add("SCADENZA_SENZA_CITAZIONE");
        } else if (raw.scadenzaOfferte() != null && raw.scadenzaOfferte().valore() != null) {
            try {
                String d = LocalDate.parse(raw.scadenzaOfferte().valore().strip()).toString();
                deadline = finding(d, raw.scadenzaOfferte().pagina(), raw.scadenzaOfferte().citazione(), pages);
            } catch (DateTimeParseException e) {
                notes.add("SCADENZA_NON_LEGGIBILE");
            }
        }
        List<ServiceReq> services = new ArrayList<>();
        for (RawService s : orEmpty(raw.servizi())) {
            if (s == null || !validWorkId(s.idOpera()) || !validOptionalAmount(s.importoEuro())) {
                notes.add("SERVIZIO_SCARTATO_NON_VALIDO");
                continue;
            }
            var f = finding(s.idOpera(), s.pagina(), s.citazione(), pages);
            services.add(new ServiceReq(s.idOpera().strip().toUpperCase(Locale.ROOT), s.importoEuro(),
                    smallInt(s.numeroServizi()), smallInt(s.anni()), cut(s.descrizione(), 300), f.pagina(), f.citazione(),
                    f.verificata()));
        }
        List<TurnoverReq> turnover = new ArrayList<>();
        for (RawTurnover t : orEmpty(raw.fatturato())) {
            if (t == null || !validOptionalAmount(t.importoEuro())) {
                notes.add("FATTURATO_SCARTATO_NON_VALIDO");
                continue;
            }
            var f = finding("", t.pagina(), t.citazione(), pages);
            turnover.add(new TurnoverReq(cut(t.descrizione(), 300), t.importoEuro(), smallInt(t.anni()), f.pagina(),
                    f.citazione(), f.verificata()));
        }
        List<StaffReq> staff = new ArrayList<>();
        for (RawStaff p : orEmpty(raw.personale())) {
            if (p == null || p.figura() == null || p.figura().isBlank()) {
                continue;
            }
            var f = finding("", p.pagina(), p.citazione(), pages);
            staff.add(new StaffReq(cut(p.figura(), 200), f.pagina(), f.citazione(), f.verificata()));
        }
        List<DocReq> docs = new ArrayList<>();
        for (RawDoc d : orEmpty(raw.documenti())) {
            if (d == null || d.descrizione() == null || d.descrizione().isBlank()) {
                continue;
            }
            var f = finding("", d.pagina(), d.citazione(), pages);
            docs.add(new DocReq(cut(d.descrizione(), 300), f.pagina(), f.citazione(), f.verificata()));
        }
        return new TenderRequirements(amount, criterio, deadline, services, turnover, staff, docs, notes);
    }

    /** Merges the windows of one document: lists are joined without repeats, single values need to agree. */
    public static TenderRequirements merge(List<TenderRequirements> parts) {
        Finding<Double> amount = null;
        Finding<String> criterio = null;
        Finding<String> deadline = null;
        List<String> notes = new ArrayList<>();
        List<ServiceReq> services = new ArrayList<>();
        List<TurnoverReq> turnover = new ArrayList<>();
        List<StaffReq> staff = new ArrayList<>();
        List<DocReq> docs = new ArrayList<>();
        for (TenderRequirements p : parts) {
            amount = pick("IMPORTO_BASE_GARA", amount, p.importoBaseGara(), notes);
            criterio = pick("CRITERIO", criterio, p.criterio(), notes);
            deadline = pick("SCADENZA", deadline, p.scadenzaOfferte(), notes);
            notes.addAll(p.incertezze());
            addNew(services, p.servizi(), s -> normalise(s.idOpera() + " " + s.citazione()));
            addNew(turnover, p.fatturato(), t -> normalise(t.descrizione() + " " + t.citazione()));
            addNew(staff, p.personale(), s -> normalise(s.figura()));
            addNew(docs, p.documenti(), d -> normalise(d.descrizione()));
        }
        return new TenderRequirements(amount, criterio, deadline, services, turnover, staff, docs,
                notes.stream().distinct().limit(40).toList());
    }

    private static <T> Finding<T> pick(String field, Finding<T> current, Finding<T> next, List<String> notes) {
        if (next == null) {
            return current;
        }
        if (current == null || (!current.verificata() && next.verificata())) {
            return next;
        }
        if (current.verificata() && next.verificata() && !Objects.equals(current.valore(), next.valore())) {
            notes.add("CONFLITTO_" + field);
        }
        return current;
    }

    private static <T> void addNew(List<T> into, List<T> from, java.util.function.Function<T, String> key) {
        Set<String> seen = new java.util.HashSet<>();
        into.forEach(x -> seen.add(key.apply(x)));
        for (T x : from) {
            if (seen.add(key.apply(x))) {
                into.add(x);
            }
        }
    }

    private static <T> Finding<T> finding(T value, Integer page, String quote, Pages pages) {
        String q = cut(quote, 240);
        if (pages.contains(page, q)) {
            return new Finding<>(value, page, q, true);
        }
        Integer other = pages.find(q);
        if (other != null) {
            return new Finding<>(value, other, q, true);
        }
        return new Finding<>(value, page, q, false);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean validAmount(Double v) {
        return v != null && v > 0 && v < 10_000_000_000.0 && !v.isNaN();
    }

    private static boolean validOptionalAmount(Double v) {
        return v == null || validAmount(v);
    }

    private static boolean validWorkId(String id) {
        return id != null && id.strip().toUpperCase(Locale.ROOT).matches("[A-Z]{1,2}\\.\\d{2}");
    }

    private static Integer smallInt(Integer v) {
        return v == null || v < 0 || v > 1000 ? null : v;
    }

    private static <T> List<T> orEmpty(List<T> l) {
        return l == null ? List.of() : l.stream().limit(60).toList();
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
