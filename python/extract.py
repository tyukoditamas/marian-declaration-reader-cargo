#!/usr/bin/env python3
import logging

logging.getLogger("pdfminer").setLevel(logging.ERROR)
logging.getLogger("pdfminer.pdfpage").setLevel(logging.ERROR)

import sys, json
from pathlib import Path
import pdfplumber
import re

MRN_RE = re.compile(r"\b(25RO[A-Z0-9]{6,})\b")  # strict: must start with 25RO

def find_mrn(lines):
    """
    Find the MRN value around the 'MRN' label.
    Works when the MRN is on the next line in its own box, or slightly above/below.
    """
    for i, line in enumerate(lines):
        if re.search(r"\bMRN\b", line, re.IGNORECASE):
            # Look around the label (handles boxed layout & blank lines)
            start = max(0, i - 3)
            end   = min(len(lines), i + 8)
            window = lines[start:end]

            # 1) Try line-by-line (most reliable)
            for w in window:
                m = MRN_RE.search(w)
                if m:
                    return m.group(1)

            # 2) Fallback: join window in case the extractor split tokens
            joined = " ".join(w.strip() for w in window if w.strip())
            m = MRN_RE.search(joined)
            if m:
                return m.group(1)

            # If we got here, we saw MRN but couldn't parse a code
            return None

    # As a very last resort (if label 'MRN' not found), scan whole doc for a 25RO* code
    joined_all = " ".join(l.strip() for l in lines if l.strip())
    m = MRN_RE.search(joined_all)
    return m.group(1) if m else None


def extract_fields(text):
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    data = {}

    # 1. nume exportator
    for i, l in enumerate(lines):
        if "Exportator [13 01]" in l:
            if i + 1 < len(lines):  # make sure there's a next line
                next_line = lines[i + 1].strip()
                # extract the first word only
                m = re.match(r"^(\w+)", next_line)
                if m:
                    data["numeExportator"] = m.group(1)
            break

    # 2. mrn
    mrn = find_mrn(lines)
    if mrn:
        data["mrn"] = mrn

    # 3. data acceptarii
    for l in lines:
        if "[15 09]" in l:
            # Search for date in format dd-mm-YYYY
            m = re.search(r"(\d{2}-\d{2})-\d{4}", l)
            if m:
                data["dataDeclaratie"] = m.group(1)  # e.g. "02-09"
            break

    # 5. nrContainer
    for i, l in enumerate(lines):
        if "[19 07]" in l:
            if i + 1 < len(lines):
                next_line = lines[i + 1].strip()
                # just grab the first alphanumeric block
                m = re.match(r"([A-Z0-9]+)", next_line, re.IGNORECASE)
                if m:
                    data["nrContainer"] = m.group(1)
            break

    return data

def main(folder_path):
    results = []
    for pdf_path in Path(folder_path).glob("*.pdf"):
        try:
            with pdfplumber.open(pdf_path) as pdf:
                text = "\n".join(page.extract_text() or "" for page in pdf.pages)
            rec = extract_fields(text)
            rec["file"] = pdf_path.name
        except Exception as e:
            rec = {"file": pdf_path.name, "error": str(e)}
        results.append(rec)

    # emit JSON array to stdout
    print(json.dumps(results, ensure_ascii=False))

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: extract.py <folder>", file=sys.stderr)
        sys.exit(1)
    main(sys.argv[1])

