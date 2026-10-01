package com.gtinwmsqr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.pdf.PrintedPdfDocument;

import java.io.FileOutputStream;

public class QrPrintAdapter extends PrintDocumentAdapter {

    private final Context context;
    private final Drawable drawable;
    private PrintedPdfDocument document;

    QrPrintAdapter(Context context, Drawable drawable) {
        this.context = context;
        this.drawable = drawable;
    }

    @Override
    public void onLayout(
            PrintAttributes oldAttributes,
            PrintAttributes newAttributes,
            CancellationSignal cancellationSignal,
            LayoutResultCallback callback,
            Bundle extras) {

        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }

        PrintDocumentInfo info =
                new PrintDocumentInfo.Builder("qr.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build();

        callback.onLayoutFinished(info, true);
    }

    @Override
    public void onWrite(
            PageRange[] pages,
            ParcelFileDescriptor destination,
            CancellationSignal cancellationSignal,
            WriteResultCallback callback) {

        PrintAttributes attributes =
                new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build();

        document = new PrintedPdfDocument(context, attributes);

        PdfDocumentPageStart:
        {
            android.graphics.pdf.PdfDocument.PageInfo pageInfo =
                    new android.graphics.pdf.PdfDocument.PageInfo.Builder(
                            595, 842, 1
                    ).create();

            android.graphics.pdf.PdfDocument.Page page =
                    document.startPage(pageInfo);

            Canvas canvas = page.getCanvas();

            int width = canvas.getWidth();
            int height = canvas.getHeight();

            int size = Math.min(width, height) * 2 / 3;

            int left = (width - size) / 2;
            int top = (height - size) / 2;

            if (drawable != null) {
                drawable.setBounds(
                        left,
                        top,
                        left + size,
                        top + size
                );
                drawable.draw(canvas);
            }

            document.finishPage(page);
        }

        try {
            document.writeTo(
                    new FileOutputStream(destination.getFileDescriptor())
            );

            callback.onWriteFinished(
                    new PageRange[]{PageRange.ALL_PAGES}
            );

        } catch (Exception e) {
            callback.onWriteFailed(e.toString());

        } finally {
            document.close();
        }
    }
}
