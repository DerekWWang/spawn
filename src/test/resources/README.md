# Archived body fixtures

`survival-v1.bin` is the original fixed v1 fixture: 422 bytes, including injured
senses, fractional needs, and worn clothing.

`survival-v4.bin` is 770 bytes written by the previous release's actual
`SurvivalState.encode()` implementation, before adding learned skills. Source
JAR SHA-256:
`abc0178caac0dbf88cd2dfa5bb3e7d3f3da1aa2b5805c5402113c2d1286bafba`.
Fixture body: hunger38.125, thirst61.5, left arm10, wool jacket in slot2;
other fields are that release's defaults. The generator asserts header4 before
writing. The test reads these historical bytes directly, rather than deriving
a legacy payload with the current encoder.
