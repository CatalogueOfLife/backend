we keep a [locust.io](https://locust.io/) file for simple load testing of the API.
Install locust and simply run `locust -f matching.py` or any other of the test py files and open http://localhost:8089/ for running the tests.
`gbif_impact.py` compares GBIF species matching with the CLB matcher on GBIF occurrence names, see [GBIF-MATCH.md](../../docs/GBIF-MATCH.md).
