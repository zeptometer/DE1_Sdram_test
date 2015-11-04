test:
	sbt 'run --backend c --compile --test --genHarness --vcd --targetDir build --noCombLoop'

verilog:
	sbt 'run --backend v --targetDir build --noCombLoop'
