.PHONY: check test
check:
	python3 tools/check_publication.py
test:
	python3 -m unittest discover -s tests -v
