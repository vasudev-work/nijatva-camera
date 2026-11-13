# Host tests

These two programs run on a desktop, with no phone and no emulator. They include
the pipeline sources directly, so they exercise the same code the library ships.

```sh
cd native-gcam-processor
c++ -O2 -std=c++17 -o /tmp/synthetic tests/synthetic_test.cpp src/main/cpp/pipeline_core.cpp
c++ -O2 -std=c++17 -o /tmp/grey      tests/grey_card_test.cpp   src/main/cpp/pipeline_core.cpp
/tmp/synthetic && /tmp/grey
```

`synthetic_test.cpp` builds a 6-frame burst with known shifts and known noise, then
checks that every shift comes back exactly and that the merged frame's noise is
close to the 1/sqrt(N) ideal (it prints the ratio; anything under 0.75 passes).

`grey_card_test.cpp` puts a colour-cast grey card through white balance, the
colour matrix and the tone curve, then checks two things: the card comes out
neutral (max channel difference under 0.004) and an 18% grey lands at 0.46 in
sRGB, which is what a correctly exposed grey card should read.
