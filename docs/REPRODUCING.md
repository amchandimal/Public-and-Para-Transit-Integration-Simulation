# Reproducing the results

Start the application:

```bash
mvn spring-boot:run
```

Open <http://localhost:8080> to see the results on the dashboard, or fetch them
as CSV:

```bash
curl -s "http://localhost:8080/api/comparison/csv/stacked"              -o results/stacked.csv
curl -s "http://localhost:8080/api/comparison/csv/ablation"             -o results/ablation.csv
curl -s "http://localhost:8080/api/comparison/network-stats"            -o results/network-stats.json
curl -s "http://localhost:8080/api/comparison/csv/repeated-seeds?n=30"  -o results/repeated-seeds.csv
curl -s "http://localhost:8080/api/comparison/csv/sensitivity?seeds=10" -o results/sensitivity.csv
```

The last two re-run the experiment many times and take longer than the others.
[`results/README.md`](../results/README.md) describes the columns.
