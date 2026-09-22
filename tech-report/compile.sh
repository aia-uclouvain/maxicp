#!/bin/bash
rm -rf _minted-maxicp/
pdflatex -interaction=nonstopmode maxicp.tex
bibtex maxicp
pdflatex -interaction=nonstopmode maxicp.tex
pdflatex -interaction=nonstopmode maxicp.tex
