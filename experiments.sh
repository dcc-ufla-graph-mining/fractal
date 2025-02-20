#! /usr/bin/env bash

for nvertices in 3 4 5; do
	./gradlew jar && master_memory=4g worker_cores=1 app_class=br.ufmg.cs.systems.fractal.apps.VNSApp args="data/citeeser $nvertices 0.01 1" ./bin/fractal-custom-app.sh 2>&1 | tee 4-1-citeeser-$nvertices-0.01-1.csv
done
