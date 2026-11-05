#!/usr/bin/env python3 -I
"""Benchmark of the local AI models on synthetic fixtures (capitolato C.8).

Measures, for each model: accuracy on e-mail classification (with manipulation attempts), extraction
accuracy, valid-output rate, latency, tokens/second and memory. Uses the SAME instructions and fixed
rules as the Edge (be/edge/src/main/resources/ai/*.txt), the same JSON schemas and the same options.

    python3 -I scripts/benchmark-ai.py qwen3:8b gpt-oss:20b --out docs/benchmark

Only loopback Ollama is contacted. Fixtures are synthetic: never put real e-mails in tests/fixtures.
For the production decision, repeat it on an anonymised sample from the studio (200 e-mails,
10 disciplinari, 5 offerte tecniche), as required by C.8.
"""
import argparse, datetime, json, pathlib, statistics, sys, time, urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "be/edge/src/main/resources/ai"
FIX = ROOT / "tests/fixtures/ai-benchmark"
OLLAMA = "http://127.0.0.1:11434"
CATS = ["BUSINESS_RELEVANT", "PROTOCOL_CANDIDATE", "INTERNAL_ONLY", "ADVERTISING", "SPAM", "POSSIBLE_PHISHING",
        "UNCERTAIN", "PEC_RECEIPT", "IRRELEVANT"]
CLASS_SCHEMA = {"type": "object", "properties": {"category": {"type": "string", "enum": CATS},
                "confidence": {"type": "number"}}, "required": ["category"], "additionalProperties": False}
INVOICE_SCHEMA = {"type": "object", "properties": {"numero": {"type": "string"}, "importoEuro": {"type": "number"}},
                  "required": ["numero", "importoEuro"], "additionalProperties": False}


def post(path, body, timeout):
    req = urllib.request.Request(OLLAMA + path, json.dumps(body).encode(), {"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=timeout))


def get(path):
    return json.load(urllib.request.urlopen(OLLAMA + path, timeout=10))


def prompt(instructions, content, rules):
    tag = "bench"
    system = instructions.strip() + "\n\n" + rules.replace("{tag}", tag)
    safe = content.replace("<<<", "‹‹‹").replace(">>>", "›››")
    return system, f"<<<DATI-{tag}>>>\n{safe}\n<<<FINE-{tag}>>>"


def chat(model, system, user, schema, timeout):
    body = {"model": model, "stream": False, "keep_alive": "10m", "format": schema,
            "options": {"temperature": 0, "num_predict": 100},
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}]}
    if model.startswith("qwen3"):
        body["think"] = False
    started = time.time()
    d = post("/api/chat", body, timeout)
    seconds = time.time() - started
    evals = d.get("eval_count", 0)
    tps = evals / (d["eval_duration"] / 1e9) if d.get("eval_duration") else 0
    return d["message"]["content"], seconds, tps


def unload(model):
    try:
        post("/api/generate", {"model": model, "keep_alive": 0}, 60)
    except Exception:
        pass


def p95(values):
    return sorted(values)[max(0, int(round(0.95 * len(values))) - 1)] if values else 0


def run_model(model, timeout):
    rules = (RES / "fixed-rules.txt").read_text(encoding="utf-8").strip()
    classify = (RES / "classify-email.txt").read_text(encoding="utf-8")
    extract = (RES / "extract-invoice.txt").read_text(encoding="utf-8")
    emails = json.loads((FIX / "emails.json").read_text(encoding="utf-8"))["emails"]
    cases = json.loads((FIX / "extraction.json").read_text(encoding="utf-8"))["cases"]
    unload(model)  # so the first call includes the load time
    lat, tps, valid, correct, inj_total, inj_ok, legit_total, legit_ok = [], [], 0, 0, 0, 0, 0, 0
    misses, cold = [], None
    for e in emails:
        system, user = prompt(classify, e["text"], rules)
        try:
            content, sec, t = chat(model, system, user, CLASS_SCHEMA, timeout)
        except Exception as ex:  # noqa: BLE001
            misses.append((e["id"], f"error {type(ex).__name__}"))
            continue
        if cold is None:
            cold = sec
        else:
            lat.append(sec)
        tps.append(t)
        try:
            category = json.loads(content)["category"]
            valid += 1
        except Exception:  # noqa: BLE001
            misses.append((e["id"], "invalid output"))
            continue
        ok = category in e["expected"]
        correct += ok
        if e["id"].startswith("x"):
            inj_total += 1
            inj_ok += ok
        if e["id"].startswith("b"):
            legit_total += 1
            legit_ok += ok
        if not ok:
            misses.append((e["id"], category))
    ex_ok = ex_valid = 0
    for c in cases:
        system, user = prompt(extract, c["text"], rules)
        try:
            content, sec, t = chat(model, system, user, INVOICE_SCHEMA, timeout)
            d = json.loads(content)
            ex_valid += 1
            ex_ok += d["numero"].strip() == c["numero"] and abs(float(d["importoEuro"]) - c["importoEuro"]) < 0.01
            lat.append(sec)
            tps.append(t)
        except Exception:  # noqa: BLE001
            pass
    mem = next((m["size"] for m in get("/api/ps").get("models", []) if m["name"].startswith(model.split(":")[0])), 0)
    unload(model)
    return {"model": model, "emails": len(emails), "valid": valid, "accuracy": correct, "injection": (inj_ok, inj_total),
            "legit": (legit_ok, legit_total), "extraction": (ex_ok, len(cases)), "extraction_valid": ex_valid,
            "cold_s": cold, "lat_avg": statistics.mean(lat) if lat else 0, "lat_p95": p95(lat),
            "tps": statistics.mean(tps) if tps else 0, "mem_gb": mem / 1e9, "misses": misses}


def report(results):
    today = datetime.date.today().isoformat()
    out = [f"# Benchmark AI locale - {today}", "",
           "Dati sintetici (`tests/fixtures/ai-benchmark`), stessi prompt e schemi dell'Edge. Non sostituisce la prova sul "
           "campione reale anonimizzato dello studio (capitolato C.8).", "",
           "| Modello | Classificazione | Output validi | Email legittime | Resistenza alle manipolazioni | Estrazione | "
           "1a chiamata (con caricamento) | Latenza media / p95 | Token/s | Memoria |",
           "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"]
    for r in results:
        out.append(f"| {r['model']} | {r['accuracy']}/{r['emails']} | {r['valid']}/{r['emails']} | "
                   f"{r['legit'][0]}/{r['legit'][1]} | {r['injection'][0]}/{r['injection'][1]} | "
                   f"{r['extraction'][0]}/{r['extraction'][1]} | {r['cold_s'] or 0:.1f} s | "
                   f"{r['lat_avg']:.1f} s / {r['lat_p95']:.1f} s | {r['tps']:.0f} | {r['mem_gb']:.1f} GB |")
    out.append("")
    for r in results:
        if r["misses"]:
            out.append(f"Errori di {r['model']}: " + ", ".join(f"{i} -> {c}" for i, c in r["misses"]))
    return "\n".join(out) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("models", nargs="+")
    ap.add_argument("--timeout", type=int, default=900)
    ap.add_argument("--out", help="directory where the Markdown report is written")
    args = ap.parse_args()
    results = []
    for m in args.models:
        print(f"== {m}", file=sys.stderr, flush=True)
        results.append(run_model(m, args.timeout))
    text = report(results)
    print(text)
    if args.out:
        d = pathlib.Path(args.out)
        d.mkdir(parents=True, exist_ok=True)
        (d / f"{datetime.date.today().isoformat()}-{'_'.join(m.replace(':', '-').replace('/', '-') for m in args.models)}.md"
         ).write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
