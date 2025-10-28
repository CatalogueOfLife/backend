import time
import random
from locust import FastHttpUser, task

names = ["Buteo buteo", "Puma concolor", "Quercus robur", "Amanita muscaria", "Canis lupus", "Felis catus", "Homo sapiens", "Escherichia coli", "Drosophila melanogaster", "Arabidopsis thaliana", "Poa annua", "Oenanthe"]
authors = ["L.", "Mill.", "DC", "Linnaeus"]

class ClbUser(FastHttpUser):
  concurrency= 25

  @task(3)
  def match(self):
    with self.rest("GET", "/dataset/9910/match/nameusage", scientificName=random.choice(names), authorship=random.choice(authors), bust=random.randint(1, 99999999999)) as resp:
      pass
