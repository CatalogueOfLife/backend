#!/usr/bin/env python3
"""
Update enum parser dictionary CSV files by consulting the GBIF Vocabulary Server.

For each vocabulary listed in vocab_mapping.yaml, fetches all labels (English labels
and alternative labels) for each concept from the GBIF API and adds any missing
labels to the corresponding CSV file.

CSV format: label,ENUM_VALUE  (one per line, label is lowercased and normalised)
API base:   https://api.gbif.org/v1/vocabularies/{vocab}/concepts/{concept}
"""

import csv
import sys
import urllib.request
import urllib.error
import json
import os
import yaml

API = "https://api.gbif.org/v1/vocabularies/"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MAPPING_FILE = os.path.join(SCRIPT_DIR, "vocab_mapping.yaml")


def fetch_concept(vocab: str, concept: str) -> dict:
    url = f"{API}{vocab}/concepts/{concept}"
    try:
        with urllib.request.urlopen(url, timeout=15) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        print(f"  WARNING: HTTP {e.code} fetching {url}", file=sys.stderr)
        return {}
    except Exception as e:
        print(f"  WARNING: Failed to fetch {url}: {e}", file=sys.stderr)
        return {}


def normalise(label: str) -> str:
    """Lowercase and strip leading/trailing whitespace."""
    return label.strip().lower()


def extract_labels(concept_data: dict) -> list[str]:
    """
    Extract all useful text labels from a GBIF concept response.

    Fields consulted:
      label             – dict {lang: text}
      alternativeLabels – list of {language: str, value: str}
    """
    labels: list[str] = []

    # Primary labels – list of {language, value, ...}
    for entry in concept_data.get("label", []):
        if isinstance(entry, dict):
            labels.append(entry.get("value", ""))
        elif isinstance(entry, str):
            labels.append(entry)

    # Alternative labels – same structure
    for entry in concept_data.get("alternativeLabels", []):
        if isinstance(entry, dict):
            labels.append(entry.get("value", ""))
        elif isinstance(entry, str):
            labels.append(entry)

    return [l for l in labels if l]


def load_csv(path: str) -> list[tuple[str, str]]:
    """Load existing CSV rows as (label, enum_value) pairs."""
    rows: list[tuple[str, str]] = []
    if not os.path.exists(path):
        return rows
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.reader(f):
            if len(row) == 2:
                rows.append((row[0], row[1]))
    return rows


def save_csv(path: str, rows: list[tuple[str, str]]) -> None:
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f, lineterminator="\n")
        for label, value in rows:
            writer.writerow([label, value])


def update_vocab(vocab_name: str, csv_file: str, concepts: list[dict]) -> None:
    csv_path = os.path.join(SCRIPT_DIR, csv_file)
    rows = load_csv(csv_path)
    existing_labels = {label for label, _ in rows}

    added = 0
    for mapping in concepts:
        # Each mapping is a single-key dict: {ENUM_VALUE: gbif_concept_name}
        for enum_value, gbif_concept in mapping.items():
            print(f"  {enum_value} -> {gbif_concept}", end=" ... ", flush=True)
            data = fetch_concept(vocab_name, gbif_concept)
            if not data:
                print("skipped")
                continue

            labels = extract_labels(data)
            new_labels = []
            for raw in labels:
                norm = normalise(raw)
                if norm and norm not in existing_labels:
                    rows.append((norm, enum_value))
                    existing_labels.add(norm)
                    new_labels.append(norm)
                    added += 1

            print(f"+{len(new_labels)}" + (f" {new_labels}" if new_labels else ""))

    if added:
        save_csv(csv_path, rows)
        print(f"  => Saved {added} new label(s) to {csv_file}")
    else:
        print(f"  => No new labels for {csv_file}")


def main() -> None:
    with open(MAPPING_FILE, encoding="utf-8") as f:
        config = yaml.safe_load(f)

    for vocab in config.get("vocabularies", []):
        vocab_name = vocab["enum"]  # e.g. "EstablishmentMeans"
        csv_file   = vocab["file"]
        concepts   = vocab.get("concepts", [])
        print(f"\nProcessing {vocab_name} -> {csv_file}")
        update_vocab(vocab_name, csv_file, concepts)

    print("\nDone.")


if __name__ == "__main__":
    main()
