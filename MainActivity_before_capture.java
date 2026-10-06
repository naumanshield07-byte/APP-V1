package com.gtinwmsqr;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import org.json.JSONArray; import org.json.JSONObject;
import java.io.*; import java.util.*; import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    static final int REQ=1001;
    static final int CATALOG_REQUEST=2001;
    TextRecognizer textRecognizer;
    boolean ocrDetected=false; boolean torchOn=false; androidx.camera.core.Camera activeCamera; PreviewView preview; TextView status, info, notFound, resultTitle; EditText input; Button scanBtn; ImageView qr; LinearLayout manualPanel; FrameLayout cameraCard; ScrollView resultScroll; ImageButton flashButton; Button manualButton; Button wmsModeButton; Button textQrModeButton; Button changeModeButton; Button importCatalogButton; LinearLayout modeSelection; ImageAnalysis analysis; boolean textQrMode=false; BarcodeScanner scanner; Map<String,Product> products=new HashMap<>(); Product last;
    LinearLayout ocrQrContainer;
    static class Product { String wms, gtin, partner, status; Product(JSONObject o){gtin=o.optString("pbarcode_canonical");wms=o.optString("wms_barcode");partner=o.optString("id_partner");status=o.optString("status");} }
    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);bind();loadCatalog();scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS).build());
        textRecognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        showModeScreen();}

    void bind(){
        preview=findViewById(R.id.preview); status=findViewById(R.id.statusText); info=findViewById(R.id.productInfo); resultTitle=findViewById(R.id.resultTitle);
        ocrQrContainer=findViewById(R.id.ocrQrContainer);
        notFound=findViewById(R.id.notFoundText); input=findViewById(R.id.gtinInput); qr=findViewById(R.id.qrImage);
        manualPanel=findViewById(R.id.manualPanel); cameraCard=findViewById(R.id.cameraCard); resultScroll=findViewById(R.id.resultScroll);
        flashButton=findViewById(R.id.flashButton); manualButton=findViewById(R.id.manualButton);
        modeSelection=findViewById(R.id.modeSelection);
        wmsModeButton=findViewById(R.id.wmsModeButton);
        textQrModeButton=findViewById(R.id.textQrModeButton);
        changeModeButton=findViewById(R.id.changeModeButton);
        importCatalogButton=findViewById(R.id.importCatalogButton);

        changeModeButton.setOnClickListener(v->showModeScreen());
        importCatalogButton.setOnClickListener(v->openCatalogPicker());

        wmsModeButton.setOnClickListener(v->{textQrMode=false;openScannerMode();});
        textQrModeButton.setOnClickListener(v->{textQrMode=true;openScannerMode();});

        findViewById(R.id.findButton).setOnClickListener(v->find(input.getText().toString()));
        findViewById(R.id.againButton).setOnClickListener(v->{resultScroll.setVisibility(View.GONE);cameraCard.setVisibility(View.VISIBLE);manualPanel.setVisibility(View.GONE);notFound.setVisibility(View.GONE);startCamera();});
        findViewById(R.id.saveButton).setOnClickListener(v->saveQr()); findViewById(R.id.printButton).setOnClickListener(v->printQr());
        manualButton.setOnClickListener(v->{manualPanel.setVisibility(View.VISIBLE);input.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
        flashButton.setOnClickListener(v->toggleTorch());
    }
    void showModeScreen(){
        stopCamera();
        modeSelection.setVisibility(View.VISIBLE);
        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.GONE);
        notFound.setVisibility(View.GONE);
        status.setText("Select scanning mode");
    }

    void openScannerMode(){
        ocrDetected=false;
        modeSelection.setVisibility(View.GONE);
        cameraCard.setVisibility(View.VISIBLE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.GONE);

        // Reset OCR result UI when entering either scanner mode.
        qr.setVisibility(View.VISIBLE);
        ocrQrContainer.setVisibility(View.GONE);
        ocrQrContainer.removeAllViews();

        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},REQ);
        } else {
            startCamera();
        }
    }

    void loadCatalog(){
        File imported = new File(getFilesDir(),"imported_catalog.json");

        try{
            if(imported.exists()){
                String s = new String(
                    readAll(new FileInputStream(imported)),
                    java.nio.charset.StandardCharsets.UTF_8
                );

                Map<String,Product> importedProducts =
                    parseCatalog(s);

                products.clear();
                products.putAll(importedProducts);

                status.setText(
                    "Imported catalog loaded: "
                    + products.size()
                    + " products"
                );

                return;
            }

            try(InputStream is=getAssets().open("catalog.json")){
                String s=new String(
                    readAll(is),
                    java.nio.charset.StandardCharsets.UTF_8
                );

                Map<String,Product> bundledProducts =
                    parseCatalog(s);

                products.clear();
                products.putAll(bundledProducts);
            }

        }catch(Exception e){
            status.setText(
                "Catalog load error: " + e.getMessage()
            );
        }
    }


    Map<String,Product> parseCatalog(String json) throws Exception{

        JSONArray a = new JSONArray(json);

        if(a.length() == 0){
            throw new Exception("Catalog is empty");
        }

        Map<String,Product> parsed = new HashMap<>();

        for(int i=0;i<a.length();i++){

            JSONObject object = a.getJSONObject(i);
            Product p = new Product(object);

            String gtin =
                p.gtin == null ? "" : p.gtin.trim();

            String wms =
                p.wms == null ? "" : p.wms.trim();

            if(gtin.isEmpty()){
                throw new Exception(
                    "Missing pbarcode_canonical at row "
                    + (i + 1)
                );
            }

            if(wms.isEmpty()){
                throw new Exception(
                    "Missing wms_barcode at row "
                    + (i + 1)
                );
            }

            p.gtin = gtin;
            p.wms = wms;

            parsed.put(gtin,p);
        }

        return parsed;
    }


    void openCatalogPicker(){

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent,CATALOG_REQUEST);
    }


    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data){

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        );

        if(requestCode != CATALOG_REQUEST
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null){

            return;
        }

        try{

            String json;

            try(InputStream is =
                    getContentResolver().openInputStream(data.getData())){

                if(is == null){
                    throw new Exception(
                        "Unable to open selected file"
                    );
                }

                json = new String(
                    readAll(is),
                    java.nio.charset.StandardCharsets.UTF_8
                );
            }

            Map<String,Product> newProducts =
                parseCatalog(json);

            File destination =
                new File(
                    getFilesDir(),
                    "imported_catalog.json"
                );

            File temporary =
                new File(
                    getFilesDir(),
                    "imported_catalog.json.tmp"
                );

            try(FileOutputStream out =
                    new FileOutputStream(temporary)){

                out.write(
                    json.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                    )
                );

                out.flush();
            }

            if(destination.exists()
                    && !destination.delete()){

                throw new Exception(
                    "Could not replace previous catalog"
                );
            }

            if(!temporary.renameTo(destination)){

                throw new Exception(
                    "Could not save imported catalog"
                );
            }

            int oldCount = products.size();

            products.clear();
            products.putAll(newProducts);

            status.setText(
                "Catalog updated: "
                + products.size()
                + " products"
            );

            Toast.makeText(
                this,
                "Catalog imported successfully: "
                + products.size()
                + " products",
                Toast.LENGTH_LONG
            ).show();

        }catch(Exception e){

            Toast.makeText(
                this,
                "Catalog import failed: "
                + e.getMessage(),
                Toast.LENGTH_LONG
            ).show();

            status.setText(
                "Catalog import failed"
            );
        }
    }


    byte[] readAll(InputStream i)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=i.read(b))>0)o.write(b,0,n);return o.toByteArray();}
    void toggleTorch(){
        if(activeCamera==null){ Toast.makeText(this,"Camera is still starting",Toast.LENGTH_SHORT).show(); return; }
        try { torchOn=!torchOn; activeCamera.getCameraControl().enableTorch(torchOn); } catch(Exception e){ Toast.makeText(this,"Flashlight unavailable",Toast.LENGTH_SHORT).show(); }
    }
    void startCamera(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            status.setText("Camera permission is required.");
            return;
        }

        ListenableFuture<ProcessCameraProvider> f =
            ProcessCameraProvider.getInstance(this);

        f.addListener(()->{
            try{
                ProcessCameraProvider cp=f.get();

                Preview p=new Preview.Builder().build();
                p.setSurfaceProvider(preview.getSurfaceProvider());

                analysis=new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

                if(textQrMode){
                    startOcrAnalyzer(analysis);
                }else{
                    analysis.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        image->{
                            InputImage ii=InputImage.fromMediaImage(
                                image.getImage(),
                                image.getImageInfo().getRotationDegrees()
                            );

                            scanner.process(ii)
                                .addOnSuccessListener(bs->{
                                    for(com.google.mlkit.vision.barcode.common.Barcode x:bs){
                                        String v=x.getRawValue();

                                        if(v!=null&&!v.isEmpty()){
                                            runOnUiThread(()->find(v));
                                            break;
                                        }
                                    }
                                })
                                .addOnCompleteListener(x->image.close());
                        }
                    );
                }

                cp.unbindAll();

                activeCamera=cp.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    p,
                    analysis
                );

                status.setText(
                    textQrMode
                        ? "OCR camera ready — point at the GTIN text."
                        : "Camera ready — scan the product GTIN."
                );

            }catch(Exception e){
                status.setText("Camera error: "+e.getMessage());
            }
        },ContextCompat.getMainExecutor(this));
    }

    void startOcrAnalyzer(ImageAnalysis analysis){
        analysis.setAnalyzer(
            Executors.newSingleThreadExecutor(),
            image -> {

                InputImage imageInput = InputImage.fromMediaImage(
                    image.getImage(),
                    image.getImageInfo().getRotationDegrees()
                );

                textRecognizer.process(imageInput)
                    .addOnSuccessListener(result -> {

                        if(ocrDetected){
                            image.close();
                            return;
                        }

                        java.util.LinkedHashSet<String> gtins =
                            new java.util.LinkedHashSet<>();

                        java.util.LinkedHashSet<String> wmsCodes =
                            new java.util.LinkedHashSet<>();

                        java.util.LinkedHashSet<String> locations =
                            new java.util.LinkedHashSet<>();

                        for(Text.TextBlock block : result.getTextBlocks()){

                            for(Text.Line line : block.getLines()){

                                String lineText = line.getText();

                                if(lineText == null || lineText.trim().isEmpty()){
                                    continue;
                                }

                                classifyOcrLine(
                                    lineText,
                                    gtins,
                                    wmsCodes,
                                    locations
                                );
                            }
                        }

                        if(!gtins.isEmpty()
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
                        }

                        image.close();

                    })
                    .addOnFailureListener(e -> image.close());
            }
        );
    }


    void classifyOcrLine(
        String raw,
        java.util.Set<String> gtins,
        java.util.Set<String> wmsCodes,
        java.util.Set<String> locations){

        String text = raw.trim();

        if(text.isEmpty()){
            return;
        }

        /*
         * Remove common OCR labels so values such as:
         * GTIN: 9880000038750
         * WMS: 11649903984P
         * LOCATION: DS28-03-01-04A
         * can also be recognized.
         */
        text = text.replaceAll(
            "(?i)\\b(PBARCODE|P-BARCODE|GTIN|WMS|BARCODE|LOCATION|LOC)\\s*[:#-]?\\s*",
            " "
        ).trim();

        /*
         * A line may contain more than one OCR element.
         * Check the complete line first, then individual tokens.
         */
        java.util.ArrayList<String> candidates =
            new java.util.ArrayList<>();

        candidates.add(text);

        String[] parts = text.split("\\s+");

        for(String part : parts){
            if(part != null && !part.trim().isEmpty()){
                candidates.add(part.trim());
            }
        }

        for(String candidate : candidates){

            String value = candidate.trim();

            if(value.isEmpty()){
                continue;
            }

            /*
             * Remove OCR punctuation around a value.
             * Keep internal hyphens because Location uses them.
             */
            value = value.replaceAll(
                "^[^A-Za-z0-9]+|[^A-Za-z0-9]+$",
                ""
            );

            if(value.isEmpty()){
                continue;
            }

            String normalized = value
                .toUpperCase(java.util.Locale.US)
                .replaceAll("\\s+", "");

            /*
             * 1. WMS BARCODE
             *
             * Catalog values look like:
             * 11649903984P
             */
            /*
             * 1. WMS BARCODE
             *
             * Accept catalog WMS values AND unknown WMS numbers
             * ending with P.
             *
             * Example:
             * 11649903984P
             */
            if(isKnownWms(normalized)
                    || normalized.matches("\\d{8,14}P")){

                wmsCodes.add(normalized);
                continue;
            }

            /*
             * 2. GTIN / P-BARCODE
             *
             * Accept catalog GTIN values AND unknown numeric
             * GTIN / barcode values.
             *
             * Supports 8-14 digit numeric values.
             */
            if(isKnownGtin(normalized)
                    || normalized.matches("\\d{8,14}")){

                gtins.add(normalized);
                continue;
            }

            /*
             * 3. LOCATION
             *
             * Example:
             * DS28-03-01-04A
             *
             * This deliberately requires the structured
             * warehouse-location format rather than accepting
             * arbitrary OCR text.
             */
            /*
             * 3. LOCATION
             *
             * Validate against the exact warehouse location
             * formats. OCR correction is position-aware and
             * only accepted when the corrected value becomes
             * a valid location.
             */
            String correctedLocation = correctOcrLocation(normalized);

            if(correctedLocation != null){
                locations.add(correctedLocation);
            }
        }
    }


    boolean isKnownGtin(String value){

        if(value == null || value.isEmpty()){
            return false;
        }

        if(products.containsKey(value)){
            return true;
        }

        for(Product product : products.values()){

            if(product.gtin != null
                    && product.gtin.trim().equalsIgnoreCase(value)){

                return true;
            }
        }

        return false;
    }


    boolean isKnownWms(String value){

        if(value == null || value.isEmpty()){
            return false;
        }

        for(Product product : products.values()){

            if(product.wms != null
                    && product.wms.trim().equalsIgnoreCase(value)){

                return true;
            }
        }

        return false;
    }


    boolean isLocation(String value){

        if(value == null){
            return false;
        }

        /*
         * Exact warehouse location formats:
         *
         * 1. DSXXX-XX-XX-XXZ
         * 2. DSXXX-CHR-XX-XXZ
         * 3. DSXXX-FZR-XX-XXZ
         * 4. DSXXX-MDRXX-XXZ
         * 5. DSXXX-HDRXX-XXZ
         * 6. DSXXX-BSKTXX-XX
         * 7. DSXXX-HOOKXX-XXZ
         *
         * X = 0-9
         * Z = A-H
         * DS = constant
         */
        return value.matches(
            "(?i)"
            + "(?:"
            + "DS\\d{2,3}-\\d{2}-\\d{2}-\\d{2}[A-H]"
            + "|DS\\d{2,3}-CHR-\\d{2}-\\d{2}[A-H]"
            + "|DS\\d{2,3}-FZR-\\d{2}-\\d{2}[A-H]"
            + "|DS\\d{2,3}-MDR\\d{2}-\\d{2}[A-H]"
            + "|DS\\d{2,3}-HDR\\d{2}-\\d{2}[A-H]"
            + "|DS\\d{2,3}-BSKT\\d{2}-\\d{2}"
            + "|DS\\d{2,3}-HOOK\\d{2}-\\d{2}[A-H]"
            + ")"
        );
    }


    String correctOcrLocation(String value){

        if(value == null || value.isEmpty()){
            return null;
        }

        String normalized = value
            .toUpperCase(java.util.Locale.US)
            .replaceAll("\\s+", "");

        /*
         * First accept an already-valid location exactly as OCR read it.
         */
        if(isLocation(normalized)){
            return normalized;
        }

        /*
         * OCR commonly reads the digit '1' as the letter 'T'.
         *
         * Only correct T when it occupies a numeric position
         * in the DSXXX section. The result must then pass the
         * strict location validator.
         *
         * Example:
         * DST20-04-02-02C
         *       ↓
         * DS120-04-02-02C
         */
        if(normalized.matches(
                "(?i)DST\\d{2}-\\d{2}-\\d{2}-\\d{2}[A-H]"
        )){
            String corrected = "DS1" + normalized.substring(3);

            if(isLocation(corrected)){
                return corrected;
            }
        }

        return null;
    }


    String normalizeTextQrGtin(String value){

        if(value == null){
            return "";
        }

        String normalized = value.trim();

        while(normalized.length() > 1
                && normalized.charAt(0) == '0'){

            normalized = normalized.substring(1);
        }

        return normalized;
    }


    void showOcrResults(
        java.util.ArrayList<String> gtins,
        java.util.ArrayList<String> wmsCodes,
        java.util.ArrayList<String> locations){

        stopCamera();

        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.VISIBLE);
        notFound.setVisibility(View.GONE);

        /*
         * Feature 2 uses its own QR container.
         * Hide the original single-QR view used by Feature 1.
         */
        qr.setVisibility(View.GONE);
        ocrQrContainer.setVisibility(View.VISIBLE);
        ocrQrContainer.removeAllViews();

        resultTitle.setText("OCR RESULTS");

        StringBuilder detectedInfo = new StringBuilder();

        if(!gtins.isEmpty()){
            detectedInfo.append("Pbarcode / GTIN detected: ")
                .append(gtins.size())
                .append("\n");
        }

        if(!wmsCodes.isEmpty()){
            detectedInfo.append("WMS barcode detected: ")
                .append(wmsCodes.size())
                .append("\n");
        }

        if(!locations.isEmpty()){
            detectedInfo.append("Location detected: ")
                .append(locations.size())
                .append("\n");
        }

        info.setText(detectedInfo.toString().trim());

        /*
         * Required order:
         * 1. GTIN
         * 2. WMS
         * 3. LOCATION
         */
        for(String value : gtins){
            addOcrQrCard(
                "GTIN / P-BARCODE",
                normalizeTextQrGtin(value)
            );
        }

        for(String value : wmsCodes){
            addOcrQrCard("WMS BARCODE", value);
        }

        for(String value : locations){
            addOcrQrCard("LOCATION", value);
        }

        status.setText(
            "OCR complete — "
            + (gtins.size() + wmsCodes.size() + locations.size())
            + " QR"
            + ((gtins.size() + wmsCodes.size() + locations.size()) == 1 ? "" : "s")
            + " generated."
        );

        resultScroll.post(() -> resultScroll.requestFocus());
    }


    void addOcrQrCard(String type, String value){

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(18,18,18,18);
        card.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText(type);
        title.setTextColor(Color.rgb(255,193,7));
        title.setTextSize(17);
        title.setTypeface(null,android.graphics.Typeface.BOLD);

        TextView content = new TextView(this);
        content.setText(value);
        content.setTextColor(Color.WHITE);
        content.setTextSize(16);
        content.setPadding(0,8,0,8);

        ImageView image = new ImageView(this);
        image.setLayoutParams(
            new LinearLayout.LayoutParams(280,280)
        );
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        try{
            image.setImageBitmap(makeQr(value,800));
        }catch(Exception e){
            content.setText(
                value + "\n\nQR error: " + e.getMessage()
            );
        }

        LinearLayout actions = createActionRow(
            () -> copyValue(content.getText().toString()),
            () -> editValueDialog(
                "Edit " + type,
                content.getText().toString(),
                newValue -> {

                    if(newValue.trim().isEmpty()) return;

                    String updated = newValue.trim();
                    content.setText(updated);

                    try{
                        image.setImageBitmap(
                            makeQr(updated,800)
                        );

                        status.setText(
                            type + " updated — QR regenerated."
                        );

                    }catch(Exception e){
                        status.setText(
                            "QR error: " + e.getMessage()
                        );
                    }
                }
            )
        );

        card.addView(title);
        card.addView(content);
        card.addView(image);
        card.addView(actions);

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );

        params.setMargins(0,0,0,24);

        ocrQrContainer.addView(card,params);
    }

    void showSameQrResult(String text){
        /*
         * Kept as a compatibility method for the previous OCR
         * implementation. Feature 2 now uses showOcrResults().
         */
        if(text == null || text.trim().isEmpty()){
            return;
        }

        java.util.LinkedHashSet<String> gtins =
            new java.util.LinkedHashSet<>();

        java.util.LinkedHashSet<String> wms =
            new java.util.LinkedHashSet<>();

        java.util.LinkedHashSet<String> locations =
            new java.util.LinkedHashSet<>();

        classifyOcrLine(text, gtins, wms, locations);

        if(!gtins.isEmpty()
                || !wms.isEmpty()
                || !locations.isEmpty()){

            showOcrResults(
                new java.util.ArrayList<>(gtins),
                new java.util.ArrayList<>(wms),
                new java.util.ArrayList<>(locations)
            );
        }
    }

    void showOcrPreview(String text){
        String cleaned=text.trim();
        if(cleaned.isEmpty())return;

        status.setText("OCR detected: " + cleaned.replace("\\n"," | "));
    }

    void stopCamera(){try{if(analysis!=null)analysis.clearAnalyzer(); if(activeCamera!=null)activeCamera.getCameraControl().enableTorch(false);}catch(Exception ignored){}activeCamera=null;analysis=null;status.setText("Camera stopped.");}
    void find(String v){
        String key=(v==null?"":v).trim();
        input.setText(key);
        notFound.setVisibility(View.GONE);

        if(key.isEmpty())return;

        Product p=products.get(key);

        if(p==null){
            notFound.setText(
                "Product not found\n\n" +
                "No matching pbarcode_canonical was found in this catalog.\n\n" +
                "GTIN: "+key
            );
            notFound.setVisibility(View.VISIBLE);
            return;
        }

        last=p;
        stopCamera();
        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.VISIBLE);

        info.setText(
            "GTIN / pbarcode_canonical:  "+p.gtin+
            "\nWMS barcode:  "+p.wms+
            "\nPartner ID:  "+p.partner+
            "\nStatus:  "+p.status
        );

        addFeature1Actions(p);

        try{
            qr.setVisibility(View.VISIBLE);
            ocrQrContainer.setVisibility(View.GONE);
            qr.setImageBitmap(makeQr(p.wms,800));
        }catch(Exception e){
            status.setText("QR error: "+e.getMessage());
        }

        resultScroll.post(()->resultScroll.requestFocus());
    }

    LinearLayout createActionRow(
        Runnable copyAction,
        Runnable editAction){

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        row.setPadding(0,8,0,4);

        TextView copy = new TextView(this);
        copy.setText("⧉  COPY");
        copy.setTextColor(Color.rgb(255,193,7));
        copy.setTextSize(15);
        copy.setTypeface(null,android.graphics.Typeface.BOLD);
        copy.setGravity(android.view.Gravity.CENTER);
        copy.setPadding(18,12,18,12);

        copy.setOnClickListener(v -> copyAction.run());

        TextView edit = new TextView(this);
        edit.setText("✎  EDIT");
        edit.setTextColor(Color.rgb(255,193,7));
        edit.setTextSize(15);
        edit.setTypeface(null,android.graphics.Typeface.BOLD);
        edit.setGravity(android.view.Gravity.CENTER);
        edit.setPadding(18,12,18,12);

        edit.setOnClickListener(v -> editAction.run());

        row.addView(
            copy,
            new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
            )
        );

        row.addView(
            edit,
            new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
            )
        );

        return row;
    }

    void copyValue(String value){

        android.content.ClipboardManager clipboard =
            (android.content.ClipboardManager)
            getSystemService(CLIPBOARD_SERVICE);

        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                "Scanned value",
                value
            )
        );

        if(android.os.Build.VERSION.SDK_INT <=
                android.os.Build.VERSION_CODES.S_V2){

            Toast.makeText(
                this,
                "Copied: " + value,
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    interface ValueEditor{
        void onValue(String value);
    }

    void editValueDialog(
        String title,
        String currentValue,
        ValueEditor editor){

        EditText editText = new EditText(this);
        editText.setText(currentValue);
        editText.setSingleLine(true);
        editText.setSelectAllOnFocus(true);

        new android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(editText)
            .setNegativeButton("CANCEL",null)
            .setPositiveButton(
                "UPDATE",
                (dialog,which) -> {
                    editor.onValue(
                        editText.getText().toString().trim()
                    );
                }
            )
            .show();
    }

    void addFeature1Actions(Product p){

        View old = resultScroll.findViewWithTag(
            "feature1Actions"
        );

        if(old != null){
            ViewGroup oldParent =
                (ViewGroup)old.getParent();

            if(oldParent != null){
                oldParent.removeView(old);
            }
        }

        ViewGroup parent =
            (ViewGroup)info.getParent();

        LinearLayout wrapper =
            new LinearLayout(this);

        wrapper.setOrientation(
            LinearLayout.VERTICAL
        );

        wrapper.setTag("feature1Actions");

        TextView gtinLabel =
            new TextView(this);

        gtinLabel.setText("SCANNED GTIN");
        gtinLabel.setTextColor(
            Color.rgb(255,193,7)
        );
        gtinLabel.setTextSize(14);
        gtinLabel.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        );

        wrapper.addView(gtinLabel);

        wrapper.addView(
            createActionRow(
                () -> copyValue(p.gtin),
                () -> editValueDialog(
                    "EDIT GTIN",
                    p.gtin,
                    newValue -> {
                        if(newValue.isEmpty()) return;
                        find(newValue);
                    }
                )
            )
        );

        TextView wmsLabel =
            new TextView(this);

        wmsLabel.setText("WMS BARCODE");
        wmsLabel.setTextColor(
            Color.rgb(255,193,7)
        );
        wmsLabel.setTextSize(14);
        wmsLabel.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        );
        wmsLabel.setPadding(0,12,0,0);

        wrapper.addView(wmsLabel);

        wrapper.addView(
            createActionRow(
                () -> copyValue(
                    last == null ? p.wms : last.wms
                ),
                () -> editValueDialog(
                    "EDIT WMS",
                    last == null ? p.wms : last.wms,
                    newValue -> {

                        if(newValue.isEmpty()) return;

                        if(last != null){
                            last.wms = newValue;
                        }

                        try{
                            qr.setImageBitmap(
                                makeQr(newValue,800)
                            );

                            if(last != null){
                                info.setText(
                                    "GTIN / pbarcode_canonical:  "+
                                    last.gtin+
                                    "\nWMS barcode:  "+
                                    last.wms+
                                    "\nPartner ID:  "+
                                    last.partner+
                                    "\nStatus:  "+
                                    last.status
                                );
                            }

                            status.setText(
                                "WMS updated — QR regenerated."
                            );

                        }catch(Exception e){
                            status.setText(
                                "QR error: " +
                                e.getMessage()
                            );
                        }
                    }
                )
            )
        );

        int index = parent.indexOfChild(info);

        parent.addView(
            wrapper,
            index + 1,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        );
    }

    Bitmap makeQr(String text,int size)throws WriterException{BitMatrix m=new MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,size,size);Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);for(int y=0;y<size;y++)for(int x=0;x<size;x++)b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);return b;}
    void saveQr(){if(last==null)return;Bitmap b;try{b=makeQr(last.wms,1000);}catch(Exception e){Toast.makeText(this,"QR generation failed: "+e.getMessage(),Toast.LENGTH_LONG).show();return;}String name="QR-"+last.wms+".png";ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/png");v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/GTIN-WMS-QR");try{android.net.Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);try(OutputStream o=getContentResolver().openOutputStream(u)){b.compress(Bitmap.CompressFormat.PNG,100,o);}Toast.makeText(this,"QR saved to Pictures/GTIN-WMS-QR",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Save failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    void printQr(){if(last==null)return;PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);pm.print("GTIN-WMS-QR-"+last.wms,new QrPrintAdapter(this,qr.getDrawable()),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());}
    @Override public void onBackPressed(){
        if(modeSelection!=null && modeSelection.getVisibility()==View.VISIBLE){
            super.onBackPressed();
            return;
        }

        showModeScreen();
    }

    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startCamera();else status.setText("Camera permission denied. You can still enter a GTIN manually.");}
}
