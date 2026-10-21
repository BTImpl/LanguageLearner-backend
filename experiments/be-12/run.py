#!/usr/bin/env python3
"""BE-12 throwaway experiment: photo of a printed worksheet -> quiz JSON via the Gemini API.

Not part of the product and not committed (see .git/info/exclude). Python 3 standard library only.

Usage (from this folder):
  export GEMINI_API_KEY=...              # free Google AI Studio key; never paste it into chat or commit it
  python3 run.py --list-models           # models that support generateContent (pick a Flash-Lite one)
  python3 run.py --model <model-id>      # process every image in ./photos, write ./results
  python3 run.py --model <model-id> --only worksheet1.jpg

Outputs in ./results: <photo>.raw.json (model answer as received), <photo>.json (validated quiz),
summary.csv (fill the empty columns by hand while comparing with the paper), summary.md.
"""
import argparse, base64, csv, json, mimetypes, os, sys, time, urllib.error, urllib.request

BASE_URL = os.environ.get("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta")
MIME = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp",
        ".heic": "image/heic", ".heif": "image/heif"}
MAX_BYTES = 8 * 1024 * 1024  # same limit as the planned BE-14 endpoint

# Same wording as the planned BE-13 prompt: extract only, never answer.
PROMPT = """You are reading a photo of a printed language-learning worksheet that contains multiple-choice questions.
Extract the questions and their answer options exactly as printed.

Rules:
- Do NOT answer the questions. Do NOT mark, suggest or hint at a correct option.
- Copy the text as printed, in its original language. Keep blanks as they are (for example "___").
- Keep each option's label as printed (a, b, c, d or 1, 2, 3 ...). If there is no printed label, use a, b, c ... in order.
- Number the questions as printed; if there is no printed number, number them from 1 in order.
- Ignore any handwriting, ticks, circles or underlines: they are not part of the question text.
- If a question or an option is unreadable or cut off, leave that question out entirely. Never guess or invent text.
- If the sheet has a heading or title, return it as "title"; otherwise return null.
- Questions without answer options (open questions, gap-fill without choices) are not multiple-choice: leave them out.
Return only JSON that matches the given schema."""

SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "title": {"type": "STRING", "nullable": True},
        "questions": {"type": "ARRAY", "items": {"type": "OBJECT", "properties": {
            "number": {"type": "INTEGER"},
            "text": {"type": "STRING"},
            "options": {"type": "ARRAY", "items": {"type": "OBJECT", "properties": {
                "label": {"type": "STRING"}, "text": {"type": "STRING"}},
                "required": ["label", "text"]}}},
            "required": ["number", "text", "options"]}}},
    "required": ["title", "questions"],
}


def api_key():
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not key:
        sys.exit("GEMINI_API_KEY is not set. Create a free key in Google AI Studio and export it in this shell.")
    return key


def http_json(url, key, body=None, timeout=120):
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="GET" if body is None else "POST",
                                 headers={"x-goog-api-key": key, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def error_message(exc):
    """Readable error without ever echoing the key."""
    if isinstance(exc, urllib.error.HTTPError):
        try:
            detail = json.loads(exc.read().decode("utf-8")).get("error", {})
            return f"HTTP {exc.code}: {detail.get('status', '')} {detail.get('message', '')}".strip()
        except Exception:
            return f"HTTP {exc.code}"
    return f"{type(exc).__name__}: {exc}"


def list_models(key):
    page, rows = None, []
    while True:
        url = f"{BASE_URL}/models?pageSize=100" + (f"&pageToken={page}" if page else "")
        data = http_json(url, key)
        for m in data.get("models", []):
            if "generateContent" in m.get("supportedGenerationMethods", []):
                rows.append(m["name"].removeprefix("models/"))
        page = data.get("nextPageToken")
        if not page:
            return sorted(rows)


def call_model(key, model, image_bytes, mime):
    body = {
        "contents": [{"parts": [
            {"inline_data": {"mime_type": mime, "data": base64.b64encode(image_bytes).decode("ascii")}},
            {"text": PROMPT}]}],
        "generationConfig": {"temperature": 0, "responseMimeType": "application/json", "responseSchema": SCHEMA},
    }
    url = f"{BASE_URL}/models/{model}:generateContent"
    last = None
    for attempt in range(3):  # retry rate limits / transient server errors
        try:
            return http_json(url, key, body)
        except urllib.error.HTTPError as exc:
            last = exc
            if exc.code in (429, 500, 502, 503, 504) and attempt < 2:
                time.sleep(5 * (attempt + 1))
                continue
            raise
    raise last


def answer_text(resp):
    block = (resp.get("promptFeedback") or {}).get("blockReason")
    if block:
        raise ValueError(f"prompt blocked: {block}")
    cands = resp.get("candidates") or []
    if not cands:
        raise ValueError("no candidates in response")
    parts = (cands[0].get("content") or {}).get("parts") or []
    text = "".join(p.get("text", "") for p in parts)
    if not text.strip():
        raise ValueError(f"empty answer (finishReason={cands[0].get('finishReason')})")
    return text


def validate(quiz):
    """Same rules as the planned BE-13 validation: drop invalid questions and count them."""
    good, skipped = [], 0
    for q in quiz.get("questions", []):
        text = (q.get("text") or "").strip()
        opts = [{"label": (o.get("label") or "").strip(), "text": (o.get("text") or "").strip()}
                for o in q.get("options", [])]
        if not text or not (2 <= len(opts) <= 5) or any(not o["text"] for o in opts):
            skipped += 1
            continue
        good.append({"number": q.get("number"), "text": text, "options": opts})
    title = (quiz.get("title") or "").strip() or None
    return {"title": title, "questions": good}, skipped


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", default=os.environ.get("GEMINI_MODEL"), help="model id (or GEMINI_MODEL)")
    ap.add_argument("--list-models", action="store_true")
    ap.add_argument("--photos", default="photos")
    ap.add_argument("--out", default="results")
    ap.add_argument("--only", help="process just this file name from the photos folder")
    args = ap.parse_args()
    key = api_key()

    if args.list_models:
        try:
            for name in list_models(key):
                print(name)
        except Exception as exc:
            sys.exit(f"Could not list models: {error_message(exc)}")
        return
    if not args.model:
        sys.exit("Pass --model <id> (see --list-models) or set GEMINI_MODEL.")

    files = sorted(f for f in os.listdir(args.photos) if os.path.splitext(f)[1].lower() in MIME)
    if args.only:
        files = [f for f in files if f == args.only]
    if not files:
        sys.exit(f"No images found in {args.photos}/ (jpg, png, webp, heic).")
    os.makedirs(args.out, exist_ok=True)

    rows = []
    for name in files:
        path = os.path.join(args.photos, name)
        base = os.path.splitext(name)[0]
        data = open(path, "rb").read()
        row = {"file": name, "status": "", "questions_found": "", "skipped_invalid": "", "seconds": "", "model_version": "",
               "prompt_tokens": "", "output_tokens": "", "expected_questions": "", "wrong_text": "",
               "wrong_options": "", "usable_without_fix": "", "notes": ""}
        if len(data) > MAX_BYTES:
            row["status"] = f"too large ({len(data) // 1024} KB > 8 MB)"
            rows.append(row)
            print(f"{name}: {row['status']}")
            continue
        started = time.time()
        try:
            resp = call_model(key, args.model, data, MIME[os.path.splitext(name)[1].lower()])
            row["seconds"] = round(time.time() - started, 1)
            row["model_version"] = resp.get("modelVersion", "")
            usage = resp.get("usageMetadata", {})
            row["prompt_tokens"] = usage.get("promptTokenCount", "")
            row["output_tokens"] = usage.get("candidatesTokenCount", "")
            text = answer_text(resp)
            with open(os.path.join(args.out, base + ".raw.json"), "w", encoding="utf-8") as fh:
                fh.write(text)
            quiz, skipped = validate(json.loads(text))
            with open(os.path.join(args.out, base + ".json"), "w", encoding="utf-8") as fh:
                json.dump(quiz, fh, ensure_ascii=False, indent=2)
            row.update(status="ok", questions_found=len(quiz["questions"]), skipped_invalid=skipped)
            print(f"{name}: {len(quiz['questions'])} questions, {skipped} dropped, {row['seconds']} s")
        except Exception as exc:
            row["seconds"] = round(time.time() - started, 1)
            row["status"] = "error: " + error_message(exc)
            print(f"{name}: {row['status']}")
        rows.append(row)

    cols = list(rows[0].keys())
    with open(os.path.join(args.out, "summary.csv"), "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=cols)
        w.writeheader()
        w.writerows(rows)
    with open(os.path.join(args.out, "summary.md"), "w", encoding="utf-8") as fh:
        fh.write(f"# BE-12 results (model: {args.model})\n\n| " + " | ".join(cols) + " |\n|" + "---|" * len(cols) + "\n")
        for r in rows:
            fh.write("| " + " | ".join(str(r[c]) for c in cols) + " |\n")
    ok = sum(1 for r in rows if r["status"] == "ok")
    print(f"\nDone: {ok}/{len(rows)} photos processed. Fill the empty columns in {args.out}/summary.csv by hand.")


if __name__ == "__main__":
    main()
