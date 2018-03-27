# Upload data to DSI cluster
HUE
146.169.33.40:8888
miguel / miguel

SSH
146.169.33.40
miguel / ChangeMe42

# Run via spark-submit
ssh 146.169.33.40 -l miguel

spark2-submit --master local[4] graph-benchmark.jar
./bin/spark-submit --master local[*]  ../graph-benchmark/out/artifacts/graph_benchmark/graph-benchmark.jar

# Datasets
graphs: http://snap.stanford.edu/data/index.html
web data crawl: http://webdatacommons.org/hyperlinkgraph/2012-08/download.html

(definition of dense graph: https://math.stackexchange.com/questions/1526372/what-is-the-definition-of-the-density-of-a-graph)