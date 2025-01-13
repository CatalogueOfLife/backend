#!/bin/zsh

API=http://api.checklistbank.org

for key in "$@"
do
  echo "Loading subtree of $key"
  curl -s "$API/dataset/3LR/export.json?synonyms=true&flat=true&minRank=tribe&taxonID=$key" | jq -r ".[].name" >> dictionary.txt
done

cat dictionary.txt | sort | uniq > dictionary-uniq.txt

