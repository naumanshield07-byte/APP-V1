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
    boolean ocrDetected=false; boolean torchOn=false; androidx.camera.core.Camera activeCamera; PreviewView preview; TextView status, info, notFound, resultTitle; EditText input; Button scanBtn; ImageView qr; LinearLayout manualPanel; FrameLayout cameraCard; ScrollView resultScroll; ImageButton flashButton;
    ImageView captureShutter; Button manualButton; Button wmsModeButton; Button textQrModeButton; Button slKlModeButton; Button changeModeButton; Button importCatalogButton; LinearLayout modeSelection; ImageAnalysis analysis; boolean textQrMode=false; boolean slKlMode=false; BarcodeScanner scanner; Map<String,Product> products=new HashMap<>(); Product last;

    static class SlKlProduct {
        String gtin;
        String partner;
        String shelfLife;
        String keepLife;

        SlKlProduct(String gtin, String partner, String shelfLife, String keepLife){
            this.gtin = gtin;
            this.partner = partner;
            this.shelfLife = shelfLife;
            this.keepLife = keepLife;
        }
    }

    Map<String,SlKlProduct> slKlProducts = new HashMap<>();
    LinearLayout ocrQrContainer;
    LinearLayout slKlResultCard, qrCard;
    TextView slKlGtinValue, slKlPartnerValue, slKlShelfLifeValue, slKlKeepLifeValue;
    java.util.LinkedHashSet<String> bufferedGtins = new java.util.LinkedHashSet<>();
    java.util.LinkedHashSet<String> bufferedWms = new java.util.LinkedHashSet<>();
    java.util.LinkedHashSet<String> bufferedLocations = new java.util.LinkedHashSet<>();

    /* Multi-frame voting: value -> times seen in last N frames */
    java.util.HashMap<String,Integer> voteGtins = new java.util.HashMap<>();
    java.util.HashMap<String,Integer> voteWms = new java.util.HashMap<>();
    java.util.HashMap<String,Integer> voteLocations = new java.util.HashMap<>();
    static final int VOTE_THRESHOLD = 2;
    volatile long lastOcrUpdate = 0L;
    String manualButtonOriginalText = null;
    static class HistoryEntry {
        String type;    // "GTIN" | "WMS" | "LOCATION"
        String value;
        String source;  // "scan" | "OCR" | "lookup"
        long ts;
    }
    java.util.ArrayList<HistoryEntry> historyEntries = new java.util.ArrayList<>();
    LinearLayout historyPanel;
    LinearLayout historyList;
    ScrollView historyScroll;

    static class Product { String wms, gtin, partner, status; Product(JSONObject o){gtin=o.optString("pbarcode_canonical");wms=o.optString("wms_barcode");partner=o.optString("id_partner");status=o.optString("status");} }
    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);bind();loadCatalog();loadSlKlCatalog();scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS).build());
        textRecognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        showModeScreen();}

    void addHistoryButton(){
        try{
            ViewGroup container = (ViewGroup) wmsModeButton.getParent();
            if(container == null) return;

            Button historyBtn = new Button(this);
            historyBtn.setText("HISTORY");
            historyBtn.setAllCaps(false);
            historyBtn.setTextColor(Color.parseColor("#000000"));
            historyBtn.setTextSize(17);
            historyBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            historyBtn.setBackgroundResource(R.drawable.glow_button);
            historyBtn.setMinHeight((int)(64 * getResources().getDisplayMetrics().density));
            historyBtn.setOnClickListener(v -> openHistory());

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int)(64 * getResources().getDisplayMetrics().density)
            );
            lp.topMargin = (int)(8 * getResources().getDisplayMetrics().density);
            container.addView(historyBtn, lp);
        }catch(Exception e){
            status.setText("History button error: " + e.getMessage());
        }
    }

    void applyStatusBarInset(){
        try{
            android.view.View content =
                findViewById(android.R.id.content);

            if(content == null) return;

            /* Add status-bar height + small breathing room */
            int paddingTop = getStatusBarHeight()
                + (int)(8 * getResources().getDisplayMetrics().density);

            content.setPadding(0, paddingTop, 0, 0);

        }catch(Exception ignored){}
    }

    int getStatusBarHeight(){
        int id = getResources().getIdentifier(
            "status_bar_height", "dimen", "android"
        );
        if(id > 0){
            return getResources().getDimensionPixelSize(id);
        }
        /* Fallback: ~24dp */
        return (int)(24 * getResources().getDisplayMetrics().density);
    }

    void bind(){
        applyStatusBarInset();
        preview=findViewById(R.id.preview); status=findViewById(R.id.statusText); info=findViewById(R.id.productInfo); resultTitle=findViewById(R.id.resultTitle);
        ocrQrContainer=findViewById(R.id.ocrQrContainer);
        slKlResultCard=findViewById(R.id.slKlResultCard);
        qrCard=findViewById(R.id.qrCard);
        slKlGtinValue=findViewById(R.id.slKlGtinValue);
        slKlPartnerValue=findViewById(R.id.slKlPartnerValue);
        slKlShelfLifeValue=findViewById(R.id.slKlShelfLifeValue);
        slKlKeepLifeValue=findViewById(R.id.slKlKeepLifeValue);
        notFound=findViewById(R.id.notFoundText); input=findViewById(R.id.gtinInput); qr=findViewById(R.id.qrImage);
        manualPanel=findViewById(R.id.manualPanel); cameraCard=findViewById(R.id.cameraCard); resultScroll=findViewById(R.id.resultScroll);
        flashButton=findViewById(R.id.flashButton); manualButton=findViewById(R.id.manualButton);
        manualButtonOriginalText=manualButton.getText().toString();
        modeSelection=findViewById(R.id.modeSelection);
        wmsModeButton=findViewById(R.id.wmsModeButton);
        textQrModeButton=findViewById(R.id.textQrModeButton);
        slKlModeButton=findViewById(R.id.slKlModeButton);
        changeModeButton=findViewById(R.id.changeModeButton);
        importCatalogButton=findViewById(R.id.importCatalogButton);

        changeModeButton.setOnClickListener(v->showModeScreen());
        importCatalogButton.setOnClickListener(v->openCatalogPicker());

        wmsModeButton.setOnClickListener(v->{textQrMode=false;slKlMode=false;openScannerMode();});
        textQrModeButton.setOnClickListener(v->{textQrMode=true;slKlMode=false;openScannerMode();});
        slKlModeButton.setOnClickListener(v->{textQrMode=false;slKlMode=true;openScannerMode();});

        findViewById(R.id.findButton).setOnClickListener(v->{
            if(slKlMode){
                findSlKl(input.getText().toString());
            }else{
                find(input.getText().toString());
            }
        });
        findViewById(R.id.againButton).setOnClickListener(v->{
            resultScroll.setVisibility(View.GONE);
            cameraCard.setVisibility(View.VISIBLE);
            manualPanel.setVisibility(View.GONE);
            notFound.setVisibility(View.GONE);
            slKlResultCard.setVisibility(View.GONE);
            qrCard.setVisibility(View.VISIBLE);
            startCamera();
        });
        findViewById(R.id.saveButton).setOnClickListener(v->saveQr()); findViewById(R.id.printButton).setOnClickListener(v->printQr());
        manualButton.setOnClickListener(v->{
            if(textQrMode){
                captureOcrResults();
            } else {
                manualPanel.setVisibility(View.VISIBLE);
                input.requestFocus();
                ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
        flashButton.setOnClickListener(v->toggleTorch());

        addHistoryButton();
        loadHistory();

        resultTitle.setTextColor(Color.parseColor("#FCFC3D"));
        info.setTextColor(Color.parseColor("#FCFC3D"));
        notFound.setTextColor(Color.parseColor("#FCFC3D"));
    }
    void showModeScreen(){
        stopCamera();
        if(captureShutter != null){
            captureShutter.setVisibility(View.GONE);
        }
        modeSelection.setVisibility(View.VISIBLE);
        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.GONE);
        notFound.setVisibility(View.GONE);
        slKlResultCard.setVisibility(View.GONE);
        qrCard.setVisibility(View.VISIBLE);
        status.setText("Select scanning mode");
    }

    void openScannerMode(){
        ocrDetected=false;
        bufferedGtins.clear();
        bufferedWms.clear();
        bufferedLocations.clear();
        resetVotes();
        lastOcrUpdate=0L;

        if(textQrMode){
            manualButton.setVisibility(View.GONE);
            setupCaptureShutter();
            if(captureShutter.getParent() == null){
                cameraCard.addView(captureShutter);
            }
            captureShutter.setVisibility(View.VISIBLE);
        } else {
            if(captureShutter != null){
                captureShutter.setVisibility(View.GONE);
            }
            manualButton.setVisibility(View.VISIBLE);
            if(manualButtonOriginalText != null){
                manualButton.setText(manualButtonOriginalText);
            }
        }
        modeSelection.setVisibility(View.GONE);
        cameraCard.setVisibility(View.VISIBLE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.GONE);

        // Reset OCR result UI when entering either scanner mode.
        qr.setVisibility(View.VISIBLE);
        ocrQrContainer.setVisibility(View.GONE);
        ocrQrContainer.removeAllViews();
        slKlResultCard.setVisibility(View.GONE);
        qrCard.setVisibility(View.VISIBLE);

        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},REQ);
        } else {
            startCamera();
        }
    }

    void loadSlKlCatalog(){
        slKlProducts.clear();

        try(InputStream is = getAssets().open("sl_kl.csv");
            BufferedReader br = new BufferedReader(
                new InputStreamReader(
                    is,
                    java.nio.charset.StandardCharsets.UTF_8
                )
            )){

            String line = br.readLine();

            if(line == null){
                throw new Exception("SL/KL file is empty");
            }

            int count = 0;

            while((line = br.readLine()) != null){

                if(line.trim().isEmpty()) continue;

                String[] parts = line.split(",", -1);

                if(parts.length < 4) continue;

                String gtin = parts[0].trim();
                String partner = parts[1].trim();
                String shelfLife = parts[2].trim();
                String keepLife = parts[3].trim();

                if(gtin.isEmpty()) continue;

                slKlProducts.put(
                    gtin,
                    new SlKlProduct(
                        gtin,
                        partner,
                        shelfLife,
                        keepLife
                    )
                );

                count++;
            }

            status.setText(
                "SL/KL catalog loaded: "
                + count
                + " products"
            );

        }catch(Exception e){
            status.setText(
                "SL/KL catalog load error: "
                + e.getMessage()
            );
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
                                            runOnUiThread(()->{
                                                if(slKlMode){
                                                    findSlKl(v);
                                                }else{
                                                    find(v);
                                                }
                                            });
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
                        : slKlMode
                            ? "Camera ready — scan the product GTIN for SL/KL."
                            : "Camera ready — scan the product GTIN."
                );

            }catch(Exception e){
                status.setText("Camera error: "+e.getMessage());
            }
        },ContextCompat.getMainExecutor(this));
    }

    void setupCaptureShutter(){

        if(captureShutter != null) return;

        float density = getResources().getDisplayMetrics().density;
        int size = (int)(88 * density);
        int bottomMargin = (int)(140 * density);

        captureShutter = new ImageView(this);
        captureShutter.setImageResource(R.drawable.capture_shutter_selector);
        captureShutter.setScaleType(ImageView.ScaleType.FIT_CENTER);
        captureShutter.setClickable(true);
        captureShutter.setContentDescription("Capture");
        captureShutter.setOnClickListener(v -> captureOcrResults());

        FrameLayout.LayoutParams lp =
            new FrameLayout.LayoutParams(size, size);
        lp.gravity = android.view.Gravity.BOTTOM
                   | android.view.Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = bottomMargin;
        captureShutter.setLayoutParams(lp);
    }

    void buildHistoryPanel(){
        if(historyPanel != null) return;

        historyPanel = new LinearLayout(this);
        historyPanel.setOrientation(LinearLayout.VERTICAL);
        historyPanel.setBackgroundColor(Color.parseColor("#080808"));
        historyPanel.setVisibility(View.GONE);

        /* Header row */
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(16, 16, 16, 16);
        header.setBackgroundColor(Color.parseColor("#080808"));

        Button backBtn = new Button(this);
        backBtn.setText("\u2190");
        backBtn.setTextSize(22);
        backBtn.setTextColor(Color.BLACK);
        backBtn.setBackgroundResource(R.drawable.glow_button);
        backBtn.setOnClickListener(v -> closeHistory());
        header.addView(backBtn);

        TextView title = new TextView(this);
        title.setText("HISTORY");
        title.setTextColor(Color.parseColor("#FCFC3D"));
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
        );
        header.addView(title, tp);

        Button clearBtn = new Button(this);
        clearBtn.setText("CLEAR");
        clearBtn.setTextSize(14);
        clearBtn.setTextColor(Color.BLACK);
        clearBtn.setBackgroundResource(R.drawable.glow_button);
        clearBtn.setOnClickListener(v -> clearHistory());
        header.addView(clearBtn);

        historyPanel.addView(header);

        historyScroll = new ScrollView(this);
        historyScroll.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
        ));
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyList.setPadding(20, 8, 20, 40);
        historyScroll.addView(historyList);
        historyPanel.addView(historyScroll);

        /* Add panel to root FrameLayout (android.R.id.content) */
        android.widget.FrameLayout content =
            findViewById(android.R.id.content);
        content.addView(historyPanel, new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    void openHistory(){
        buildHistoryPanel();
        stopCamera();
        modeSelection.setVisibility(View.GONE);
        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.GONE);
        notFound.setVisibility(View.GONE);
        if(captureShutter != null) captureShutter.setVisibility(View.GONE);
        historyPanel.setVisibility(View.VISIBLE);
        renderHistory();
    }

    void closeHistory(){
        if(historyPanel != null) historyPanel.setVisibility(View.GONE);
        showModeScreen();
    }

    void loadHistory(){
        try{
            java.io.File f = new java.io.File(getFilesDir(), "history.json");
            if(!f.exists()) return;
            String s = new String(
                readAll(new java.io.FileInputStream(f)),
                java.nio.charset.StandardCharsets.UTF_8
            );
            org.json.JSONArray arr = new org.json.JSONArray(s);
            historyEntries.clear();
            for(int i = 0; i < arr.length(); i++){
                org.json.JSONObject o = arr.getJSONObject(i);
                HistoryEntry he = new HistoryEntry();
                he.type   = o.optString("type");
                he.value  = o.optString("value");
                he.source = o.optString("source", "scan");
                he.ts     = o.optLong("ts");
                historyEntries.add(he);
            }
        }catch(Exception ignored){}
    }

    void persistHistory(){
        try{
            org.json.JSONArray arr = new org.json.JSONArray();
            for(HistoryEntry he : historyEntries){
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("type",   he.type);
                o.put("value",  he.value);
                o.put("source", he.source);
                o.put("ts",     he.ts);
                arr.put(o);
            }
            try(java.io.FileOutputStream out = new java.io.FileOutputStream(
                    new java.io.File(getFilesDir(), "history.json"))){
                out.write(arr.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.flush();
            }
        }catch(Exception ignored){}
    }

    void saveHistoryEntry(String type, String value, String source){
        if(value == null || value.isEmpty()) return;

        long now = System.currentTimeMillis();
        long dedupeWindow = 30L * 60L * 1000L;  /* 30 min */

        /* Remove recent duplicates of same type+value */
        for(int i = historyEntries.size() - 1; i >= 0; i--){
            HistoryEntry he = historyEntries.get(i);
            if(he.type.equals(type) && he.value.equals(value)
                    && (now - he.ts) < dedupeWindow){
                historyEntries.remove(i);
            }
        }

        HistoryEntry he = new HistoryEntry();
        he.type = type; he.value = value; he.source = source; he.ts = now;
        historyEntries.add(0, he);

        while(historyEntries.size() > 50){
            historyEntries.remove(historyEntries.size() - 1);
        }

        persistHistory();
    }

    void clearHistory(){
        new android.app.AlertDialog.Builder(this)
            .setTitle("Clear history?")
            .setMessage("This removes all saved scan history.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CLEAR", (d, w) -> {
                historyEntries.clear();
                persistHistory();
                renderHistory();
            })
            .show();
    }

    void renderHistory(){
        if(historyList == null) return;
        historyList.removeAllViews();

        if(historyEntries.isEmpty()){
            TextView empty = new TextView(this);
            empty.setText("No history yet.\n\nScanned and OCR-detected values will appear here.");
            empty.setTextColor(Color.parseColor("#FCFC3D"));
            empty.setTextSize(15);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(20, 80, 20, 20);
            historyList.addView(empty);
            return;
        }

        for(HistoryEntry he : historyEntries){
            historyList.addView(buildHistoryCard(he));
        }
    }

    View buildHistoryCard(HistoryEntry he){

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 20, 24, 20);
        card.setBackgroundResource(R.drawable.glow_card);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, 16);
        card.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(he.type);
        t.setTextColor(Color.parseColor("#FCFC3D"));
        t.setTextSize(12);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setLetterSpacing(0.1f);
        card.addView(t);

        TextView v = new TextView(this);
        v.setText(he.value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(18);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(0, 8, 0, 8);
        card.addView(v);

        TextView ts = new TextView(this);
        ts.setText(formatTime(he.ts) + "   \u2022   " + he.source);
        ts.setTextColor(Color.parseColor("#999999"));
        ts.setTextSize(11);
        card.addView(ts);

        card.setOnClickListener(x -> copyValue(he.value));

        return card;
    }

    String formatTime(long ts){
        long diff = System.currentTimeMillis() - ts;
        if(diff < 60_000L)     return "just now";
        if(diff < 3_600_000L)  return (diff / 60_000L) + "m ago";
        if(diff < 86_400_000L) return (diff / 3_600_000L) + "h ago";
        return new java.text.SimpleDateFormat(
            "MMM d, HH:mm", java.util.Locale.US
        ).format(new java.util.Date(ts));
    }

    void captureOcrResults(){

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

    /*
     * Feed a candidate through the voting system. Returns true if
     * the value has been seen enough times to be trusted.
     */
    boolean vote(java.util.HashMap<String,Integer> map, String value){
        Integer c = map.get(value);
        int next = (c == null ? 1 : c + 1);
        map.put(value, next);
        return next >= VOTE_THRESHOLD;
    }

    void resetVotes(){
        voteGtins.clear();
        voteWms.clear();
        voteLocations.clear();
    }

    void startOcrAnalyzer(ImageAnalysis analysis){
        analysis.setAnalyzer(
            Executors.newSingleThreadExecutor(),
            image -> {

                InputImage imageInput = InputImage.fromMediaImage(
                    image.getImage(),
                    image.getImageInfo().getRotationDegrees()
                );

                /*
                 * STEP 1: Try the barcode decoder FIRST.
                 * Barcodes are ~99.9% accurate - use them when available.
                 * Any successfully decoded value is classified and
                 * pushed into the same buffers that CAPTURE uses.
                 */
                scanner.process(imageInput)
                    .addOnSuccessListener(barcodes -> {

                        for(com.google.mlkit.vision.barcode.common.Barcode bc : barcodes){

                            String raw = bc.getRawValue();
                            if(raw == null || raw.trim().isEmpty()) continue;

                            String upper = raw.trim().toUpperCase(java.util.Locale.US);

                            java.util.LinkedHashSet<String> bg =
                                new java.util.LinkedHashSet<>();
                            java.util.LinkedHashSet<String> bw =
                                new java.util.LinkedHashSet<>();
                            java.util.LinkedHashSet<String> bl =
                                new java.util.LinkedHashSet<>();

                            classifyOcrLine(upper, bg, bw, bl);

                            if(!bg.isEmpty() || !bw.isEmpty()){
                                ocrDetected = true;
                                final String shownValue = upper;
                                runOnUiThread(() -> {
                                    bufferedGtins.addAll(bg);
                                    bufferedWms.addAll(bw);
                                    int t = bufferedGtins.size()
                                        + bufferedWms.size()
                                        + bufferedLocations.size();
                                    status.setText(
                                        "Barcode: " + shownValue
                                        + " | Buffered: " + t
                                        + " - tap CAPTURE"
                                    );
                                });
                            }
                        }
                    })
                    .addOnCompleteListener(bcTask -> {

                        /*
                         * STEP 2: Now run OCR on the SAME frame for
                         * Location labels and any text the barcode
                         * decoder couldn't read.
                         */
                        textRecognizer.process(imageInput)
                            .addOnSuccessListener(result -> {

                        long now = System.currentTimeMillis();
                        if(ocrDetected && now - lastOcrUpdate < 300){
                            image.close();
                            return;
                        }
                        lastOcrUpdate = now;

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

                            runOnUiThread(() -> {
                                for(String g : gtins){
                                    if(vote(voteGtins, g)){
                                        bufferedGtins.add(g);
                                    }
                                }
                                for(String w : wmsCodes){
                                    if(vote(voteWms, w)){
                                        bufferedWms.add(w);
                                    }
                                }
                                for(String l : locations){
                                    if(vote(voteLocations, l)){
                                        bufferedLocations.add(l);
                                    }
                                }

                                int total = bufferedGtins.size()
                                    + bufferedWms.size()
                                    + bufferedLocations.size();

                                status.setText(
                                    "Buffered: " + total
                                    + " item(s) - tap CAPTURE to commit"
                                );
                            });
                        }

                        image.close();

                            })
                            .addOnFailureListener(e -> image.close());
                    });
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
            /*
             * Apply OCR digit-confusion correction only when the
             * candidate looks numeric. Protects alphanumeric codes.
             */
            String correctedValue = normalized;
            if(looksLikeNumericCandidate(normalized)){
                correctedValue = fixOcrDigits(normalized);
            }

            /* 1. WMS BARCODE */
            if(isKnownWms(normalized)
                    || isKnownWms(correctedValue)
                    || normalized.matches("\\d{8,14}P")
                    || correctedValue.matches("\\d{8,14}P")){

                wmsCodes.add(correctedValue);
                continue;
            }

            /* 2. GTIN / P-BARCODE (catalog or numeric) */
            if(isKnownGtin(normalized)
                    || isKnownGtin(correctedValue)
                    || normalized.matches("\\d{8,14}")
                    || correctedValue.matches("\\d{8,14}")){

                gtins.add(correctedValue);
                continue;
            }

            /* 2b. ALPHANUMERIC GTIN (non-catalog, mixed letters+digits) */
            if(!isLocation(correctedValue)
                    && correctedValue.matches("[A-Z0-9]{6,20}")
                    && correctedValue.matches(".*\\d.*")
                    && correctedValue.matches(".*[A-Z].*")
                    && !correctedValue.endsWith("P")){

                gtins.add(correctedValue);
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

    /*
     * OCR commonly swaps visually similar characters.
     * Fix them only inside digit-intended candidates.
     */
    String fixOcrDigits(String value){
        if(value == null || value.isEmpty()) return value;
        String upper = value.toUpperCase(java.util.Locale.US);
        StringBuilder out = new StringBuilder(upper.length());
        for(int i = 0; i < upper.length(); i++){
            char c = upper.charAt(i);
            switch(c){
                case 'O': case 'Q': case 'D': out.append('0'); break;
                case 'I': case 'L':           out.append('1'); break;
                case 'Z':                     out.append('2'); break;
                case 'S':                     out.append('5'); break;
                case 'G':                     out.append('6'); break;
                case 'T':                     out.append('7'); break;
                case 'B':                     out.append('8'); break;
                default:                      out.append(c);
            }
        }
        return out.toString();
    }

    boolean looksLikeNumericCandidate(String value){
        if(value == null || value.isEmpty()) return false;
        String v = value.toUpperCase(java.util.Locale.US);
        int digitish = 0;
        for(int i = 0; i < v.length(); i++){
            char c = v.charAt(i);
            if(Character.isDigit(c)){ digitish++; continue; }
            if("OQDILZSGTB".indexOf(c) >= 0){ digitish++; continue; }
            if(c == 'P' && i == v.length() - 1) continue;
            return false;
        }
        return v.length() > 0 && (digitish * 100 / v.length()) >= 70;
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

        for(String v : gtins)     saveHistoryEntry("GTIN", v, "OCR");
        for(String v : wmsCodes)  saveHistoryEntry("WMS", v, "OCR");
        for(String v : locations) saveHistoryEntry("LOCATION", v, "OCR");

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
        card.setBackgroundResource(R.drawable.glow_card);

        TextView title = new TextView(this);
        title.setText(type);
        title.setTextColor(Color.parseColor("#FCFC3D"));
        title.setTextSize(17);
        title.setTypeface(null,android.graphics.Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);

        TextView content = new TextView(this);
        content.setText(value);
        content.setTextColor(Color.WHITE);
        content.setTextSize(16);
        content.setGravity(android.view.Gravity.CENTER);
        content.setPadding(0,10,0,10);

        ImageView image = new ImageView(this);

        /* Square QR tile - 320dp on each side, centered. */
        int qrSide = (int)(320 * getResources()
                              .getDisplayMetrics().density);

        LinearLayout.LayoutParams imgParams =
            new LinearLayout.LayoutParams(qrSide, qrSide);

        imgParams.setMargins(20, 16, 20, 16);
        imgParams.gravity = android.view.Gravity.CENTER;

        image.setLayoutParams(imgParams);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.WHITE);

        try{
            image.setImageBitmap(makeQr(value,1200));
        }catch(Exception e){
            content.setText(
                value + "\n\nQR error: " + e.getMessage()
            );
        }

        LinearLayout actions = createActionRow(
            () -> editValueDialog(
                "Edit " + type,
                content.getText().toString(),
                newValue -> {

                    if(newValue.trim().isEmpty()) return;

                    String updated = newValue.trim();
                    content.setText(updated);

                    try{
                        image.setImageBitmap(
                            makeQr(updated,1200)
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
            ),
            () -> copyValue(content.getText().toString()),
            () -> shareValue(type, content.getText().toString())
        );

        card.addView(title);

        TextView qrGenerated = new TextView(this);
        qrGenerated.setText("QR GENERATED");
        qrGenerated.setTextColor(Color.BLACK);
        qrGenerated.setTextSize(15);
        qrGenerated.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        );
        qrGenerated.setGravity(android.view.Gravity.CENTER);
        qrGenerated.setPadding(16,10,16,10);
        qrGenerated.setBackgroundResource(
            R.drawable.glow_button
        );

        LinearLayout.LayoutParams qrTitleParams =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );

        qrTitleParams.gravity = android.view.Gravity.CENTER;
        qrTitleParams.setMargins(0,10,0,10);

        card.addView(qrGenerated,qrTitleParams);
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
    void findSlKl(String v){
        String key=(v==null?"":v).trim();
        input.setText(key);
        notFound.setVisibility(View.GONE);

        if(key.isEmpty()) return;

        SlKlProduct p=slKlProducts.get(key);

        if(p==null){
            stopCamera();
            cameraCard.setVisibility(View.GONE);
            manualPanel.setVisibility(View.GONE);
            resultScroll.setVisibility(View.VISIBLE);

            resultTitle.setText("PRODUCT NOT FOUND");
            info.setVisibility(View.GONE);

            slKlResultCard.setVisibility(View.GONE);
            qrCard.setVisibility(View.GONE);

            notFound.setText(
                "No matching GTIN was found in the SL/KL catalog.\n\n" +
                "GTIN: "+key
            );
            notFound.setVisibility(View.VISIBLE);

            resultScroll.post(()->resultScroll.requestFocus());
            return;
        }

        saveHistoryEntry("GTIN", p.gtin, "sl_kl");

        stopCamera();
        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.VISIBLE);

        resultTitle.setText("PRODUCT FOUND");
        info.setVisibility(View.GONE);
        notFound.setVisibility(View.GONE);

        slKlGtinValue.setText(p.gtin);
        slKlPartnerValue.setText(p.partner);

        // Blank source values remain blank; no value is guessed.
        slKlShelfLifeValue.setText(
            p.shelfLife.isEmpty() ? "—" : p.shelfLife
        );

        slKlKeepLifeValue.setText(
            p.keepLife.isEmpty() ? "—" : p.keepLife
        );

        slKlResultCard.setVisibility(View.VISIBLE);
        qrCard.setVisibility(View.GONE);

        saveButton.setVisibility(View.GONE);
        printButton.setVisibility(View.GONE);

        resultScroll.post(()->resultScroll.requestFocus());
    }

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
        saveHistoryEntry("GTIN", p.gtin, "lookup");
        stopCamera();
        cameraCard.setVisibility(View.GONE);
        manualPanel.setVisibility(View.GONE);
        resultScroll.setVisibility(View.VISIBLE);

        resultTitle.setText("PRODUCT FOUND");
        slKlResultCard.setVisibility(View.GONE);
        qrCard.setVisibility(View.VISIBLE);
        saveButton.setVisibility(View.VISIBLE);
        printButton.setVisibility(View.VISIBLE);

        info.setText("");
        info.setVisibility(View.GONE);

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
        Runnable editAction,
        Runnable copyAction,
        Runnable shareAction){

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        row.setPadding(0,6,0,4);

        TextView edit = new TextView(this);
        edit.setText("✎  EDIT");
        edit.setTextColor(Color.parseColor("#FCFC3D"));
        edit.setTextSize(15);
        edit.setTypeface(null,android.graphics.Typeface.BOLD);
        edit.setGravity(android.view.Gravity.CENTER);
        edit.setPadding(18,12,18,12);
        edit.setBackgroundResource(R.drawable.glow_dark_button);
        edit.setOnClickListener(v -> editAction.run());

        TextView copy = new TextView(this);
        copy.setText("⧉  COPY");
        copy.setTextColor(Color.parseColor("#FCFC3D"));
        copy.setTextSize(15);
        copy.setTypeface(null,android.graphics.Typeface.BOLD);
        copy.setGravity(android.view.Gravity.CENTER);
        copy.setPadding(18,12,18,12);
        copy.setBackgroundResource(R.drawable.glow_dark_button);
        copy.setOnClickListener(v -> copyAction.run());

        row.addView(
            edit,
            new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
            )
        );

        row.addView(
            copy,
            new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
            )
        );

        TextView share = new TextView(this);
        share.setText("⤴  SHARE");
        share.setTextColor(Color.parseColor("#FFFC5C"));
        share.setTextSize(15);
        share.setTypeface(null,android.graphics.Typeface.BOLD);
        share.setGravity(android.view.Gravity.CENTER);
        share.setPadding(18,12,18,12);
        share.setBackgroundResource(R.drawable.glow_dark_button);
        share.setOnClickListener(v -> shareAction.run());

        row.addView(
            share,
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

        info.setVisibility(View.GONE);

        ViewGroup parent =
            (ViewGroup)info.getParent();

        LinearLayout wrapper =
            new LinearLayout(this);

        wrapper.setOrientation(
            LinearLayout.VERTICAL
        );

        wrapper.setTag("feature1Actions");
        wrapper.setPadding(4,4,4,4);

        TextView gtinLabel =
            new TextView(this);

        gtinLabel.setText(
            "GTIN/pbarcode_canonical :  " + p.gtin
        );
        gtinLabel.setTextColor(Color.parseColor("#FCFC3D"));
        gtinLabel.setTextSize(16);
        gtinLabel.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        );
        gtinLabel.setPadding(0,4,0,0);

        wrapper.addView(gtinLabel);

        wrapper.addView(
            createActionRow(
                () -> editValueDialog(
                    "EDIT GTIN",
                    p.gtin,
                    newValue -> {
                        if(newValue.isEmpty()) return;
                        find(newValue);
                    }
                ),
                () -> copyValue(p.gtin),
                () -> shareValue("GTIN", p.gtin)
            )
        );

        TextView wmsLabel =
            new TextView(this);

        wmsLabel.setText(
            "WMS barcode :  " + p.wms
        );
        wmsLabel.setTextColor(Color.parseColor("#FCFC3D"));
        wmsLabel.setTextSize(16);
        wmsLabel.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        );
        wmsLabel.setPadding(0,14,0,0);

        wrapper.addView(wmsLabel);

        wrapper.addView(
            createActionRow(
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
                                addFeature1Actions(last);
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
                ),
                () -> copyValue(
                    last == null ? p.wms : last.wms
                ),
                () -> shareValue(
                    "WMS",
                    last == null ? p.wms : last.wms
                )
            )
        );

        TextView partnerLabel =
            new TextView(this);

        partnerLabel.setText(
            "Partner ID :  " + p.partner
        );
        partnerLabel.setTextColor(Color.parseColor("#FCFC3D"));
        partnerLabel.setTextSize(16);
        partnerLabel.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        );
        partnerLabel.setPadding(0,14,0,6);

        wrapper.addView(partnerLabel);

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

    void updateResultInformation(Product p){

        info.setText(
            "GTIN/pbarcode_canonical :  " + p.gtin +
            "\nWMS barcode :  " + p.wms +
            "\nPartner ID :  " + p.partner
        );
    }

    void shareValue(String label, String value){

        try{
            /* Render QR bitmap to cache */
            Bitmap bmp = makeQr(value, 1000);

            java.io.File dir =
                new java.io.File(getCacheDir(), "qr_share");

            if(!dir.exists()) dir.mkdirs();

            java.io.File file = new java.io.File(
                dir,
                "QR_" + System.currentTimeMillis() + ".png"
            );

            try(java.io.FileOutputStream out =
                    new java.io.FileOutputStream(file)){
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
            }

            android.net.Uri uri =
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
                );

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/png");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(
                Intent.EXTRA_TEXT,
                label + ": " + value
            );
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(
                Intent.createChooser(
                    share,
                    "Share " + label
                )
            );

        }catch(Exception e){
            /* Fallback: text-only share */
            try{
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(
                    Intent.EXTRA_TEXT,
                    label + ": " + value
                );
                startActivity(
                    Intent.createChooser(share, "Share " + label)
                );
            }catch(Exception e2){
                Toast.makeText(
                    this,
                    "Share failed: " + e2.getMessage(),
                    Toast.LENGTH_LONG
                ).show();
            }
        }
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
