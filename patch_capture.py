#!/usr/bin/env python3
import pathlib, sys

FILE = pathlib.Path.home() / "APP-V1/app/src/main/java/com/gtinwmsqr/MainActivity.java"
if not FILE.exists():
    print("ERROR: file not found"); sys.exit(1)

src = FILE.read_text(encoding="utf-8")
original = src  # for rollback inside script

def replace(old, new, label):
    global src
    if old not in src:
        print("ERROR: could not find:", label)
        print("---- expected ----")
        print(old[:400])
        sys.exit(1)
    src = src.replace(old, new, 1)
    print("OK:", label)

# ---------- 1) Add buffer fields ----------
replace(
"    LinearLayout ocrQrContainer;\n    static class Product",
"""    LinearLayout ocrQrContainer;
    java.util.LinkedHashSet<String> bufferedGtins = new java.util.LinkedHashSet<>();
    java.util.LinkedHashSet<String> bufferedWms = new java.util.LinkedHashSet<>();
    java.util.LinkedHashSet<String> bufferedLocations = new java.util.LinkedHashSet<>();
    volatile long lastOcrUpdate = 0L;
    String manualButtonOriginalText = null;
    static class Product""",
"buffer fields")

# ---------- 2) Capture original button text in bind() ----------
replace(
"manualButton=findViewById(R.id.manualButton);",
"manualButton=findViewById(R.id.manualButton);\n        manualButtonOriginalText=manualButton.getText().toString();",
"save original button text")

# ---------- 3) Mode-aware button handler ----------
replace(
"manualButton.setOnClickListener(v->{manualPanel.setVisibility(View.VISIBLE);input.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});",
"""manualButton.setOnClickListener(v->{
            if(textQrMode){
                captureOcrResults();
            } else {
                manualPanel.setVisibility(View.VISIBLE);
                input.requestFocus();
                ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });""",
"button handler")

# ---------- 4) Reset buffers + swap label in openScannerMode ----------
replace(
"    void openScannerMode(){\n        ocrDetected=false;",
"""    void openScannerMode(){
        ocrDetected=false;
        bufferedGtins.clear();
        bufferedWms.clear();
        bufferedLocations.clear();
        lastOcrUpdate=0L;

        if(textQrMode){
            manualButton.setText("CAPTURE");
        } else if(manualButtonOriginalText != null){
            manualButton.setText(manualButtonOriginalText);
        }""",
"reset buffers + label")

# ---------- 5) Throttle instead of hard stop ----------
replace(
"""                        if(ocrDetected){
                            image.close();
                            return;
                        }""",
"""                        long now = System.currentTimeMillis();
                        if(ocrDetected && now - lastOcrUpdate < 300){
                            image.close();
                            return;
                        }
                        lastOcrUpdate = now;""",
"throttle OCR loop")

# ---------- 6) Buffer instead of auto-commit ----------
replace(
"""                        if(!gtins.isEmpty()
                                || !wmsCodes.isEmpty()
                                || !locations.isEmpty()){

                            ocrDetected = true;

                            final java.util.ArrayList<String> finalGtins =
                                new java.util.ArrayList<>(gtins);

                            final java.util.ArrayList<String> finalWms =
                                new java.util.ArrayList<>(wmsCodes);

                            final java.util.ArrayList<String> finalLocations =
                                new java.util.ArrayList<>(locations);

                            runOnUiThread(() ->
                                showOcrResults(
                                    finalGtins,
                                    finalWms,
                                    finalLocations
                                )
                            );
                        }""",
"""                        if(!gtins.isEmpty()
                                || !wmsCodes.isEmpty()
                                || !locations.isEmpty()){

                            ocrDetected = true;

                            runOnUiThread(() -> {
                                bufferedGtins.addAll(gtins);
                                bufferedWms.addAll(wmsCodes);
                                bufferedLocations.addAll(locations);

                                int total = bufferedGtins.size()
                                    + bufferedWms.size()
                                    + bufferedLocations.size();

                                status.setText(
                                    "Buffered: " + total
                                    + " item(s) - tap CAPTURE to commit"
                                );
                            });
                        }""",
"buffer instead of auto-commit")

# ---------- 7) Add captureOcrResults() method ----------
replace(
"    void startOcrAnalyzer(ImageAnalysis analysis){",
"""    void captureOcrResults(){

        if(bufferedGtins.isEmpty()
                && bufferedWms.isEmpty()
                && bufferedLocations.isEmpty()){

            Toast.makeText(
                this,
                "Nothing detected yet - hold steady over the label",
                Toast.LENGTH_SHORT
            ).show();

            return;
        }

        java.util.ArrayList<String> g =
            new java.util.ArrayList<>(bufferedGtins);

        java.util.ArrayList<String> w =
            new java.util.ArrayList<>(bufferedWms);

        java.util.ArrayList<String> l =
            new java.util.ArrayList<>(bufferedLocations);

        bufferedGtins.clear();
        bufferedWms.clear();
        bufferedLocations.clear();

        showOcrResults(g, w, l);
    }

    void startOcrAnalyzer(ImageAnalysis analysis){""",
"captureOcrResults method")

FILE.write_text(src, encoding="utf-8")
print()
print("ALL PATCHES APPLIED")
print("Saved:", FILE)
