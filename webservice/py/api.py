import time
import random
from locust import HttpUser, task, between

CHARS = "23456789BCDFGHJKLMNPQRSTVWXYZ"
def rndCOLID():
  num = random.randint(2, 5)
  id = ""
  for i in range(num):
    id += random.choice(CHARS)
  return id


class ClbUser(HttpUser):
  wait_time = between(1, 5)

  @task(3)
  def assembly(self):
    self.client.get("/dataset/3/assembly")

  @task
  def dataset(self):
    self.client.get("/dataset/3LR")

  @task(2)
  def passer(self):
    self.client.get("/dataset/3LR/taxon/4DXXM")
    self.client.get("/dataset/3LR/taxon/4DXXM/info")

  @task(10)
  def taxon(self):
    id = rndCOLID()
    self.client.get("/dataset/3LR/taxon/"+id)

  @task
  def logo(self):
    self.client.get("/image/2232/logo?size=ORIGINAL")
