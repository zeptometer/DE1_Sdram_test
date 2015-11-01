test:
	sbt 'run --backend c --compile --test --genHarness --vcd --targetDir build'

verilog:
	sbt 'run --backend v --targetDir build'
