package ca.uwaterloo.cs349.pdfreader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not obvious from documentation, so read this carefully before making changes
// to the PDF display code.

public class MainActivity extends AppCompatActivity {

    final String LOGNAME = "pdf_viewer";
    final String FILENAME = "shannon1948.pdf";
    final int FILERESID = R.raw.shannon1948;

    // manage the pages of the PDF, see below
    static PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private static PdfRenderer.Page currentPage;

    // custom ImageView class that captures strokes and draws them over the image
    static PDFimage pageImage;
    private static TextView pageNumberView;
    private Menu optionsMenu;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinearLayout layout = findViewById(R.id.pdfLayout);
        pageImage = new PDFimage(this);
        layout.addView(pageImage);
        layout.setEnabled(true);
        pageImage.setMinimumWidth(1000);
        pageImage.setMinimumHeight(2000);

        pageNumberView = findViewById(R.id.pageNumberView);
        pageNumberView.setText("1 / 55");
        pageNumberView.setTextSize(20);

        Button previousButton = findViewById(R.id.prevPageButton);
        Button nextButton = findViewById(R.id.nextPageButton);

        previousButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                pageImage.previousPage();
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                pageImage.nextPage();
            }
        });

        Button undoButton = findViewById(R.id.undoButton);
        Button redoButton = findViewById(R.id.redoButton);

        undoButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                pageImage.undo();
            }
        });

        redoButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                pageImage.redo();
            }
        });

        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            openRenderer(this);
            showPage(0);
        } catch (IOException exception) {
            Log.d(LOGNAME, "Error opening PDF");
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            closeRenderer();
        } catch (IOException ex) {
            Log.d(LOGNAME, "Unable to close PDF renderer");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void openRenderer(Context context) throws IOException {
        // In this sample, we read a PDF from the assets directory.
        File file = new File(context.getCacheDir(), FILENAME);
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            InputStream asset = this.getResources().openRawResource(FILERESID);
            FileOutputStream output = new FileOutputStream(file);
            final byte[] buffer = new byte[1024];
            int size;
            while ((size = asset.read(buffer)) != -1) {
                output.write(buffer, 0, size);
            }
            asset.close();
            output.close();
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        if (parcelFileDescriptor != null) {
            pdfRenderer = new PdfRenderer(parcelFileDescriptor);
        }
    }

    // do this before you quit!
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void closeRenderer() throws IOException {
        if (null != currentPage) {
            currentPage.close();
        }
        pdfRenderer.close();
        parcelFileDescriptor.close();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static void showPage(int index) {
        pageNumberView.setText((index+1) + " / 55");

        if (pdfRenderer.getPageCount() <= index) {
            return;
        }
        // Close the current page before opening another one.
        if (null != currentPage) {
            currentPage.close();
        }
        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index);
        // Important: the destination bitmap must be ARGB (not RGB).
        Bitmap bitmap = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);

        // Here, we render the page onto the Bitmap.
        // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
        // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        // Display the page
        pageImage.setImage(bitmap);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        getMenuInflater().inflate(R.menu.menu_layout, menu);
        optionsMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    // handle button activities
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.pencilButton) {
            Log.d("operation", "pencil");
            if(pageImage.getOperation().equals("Pencil")){
                item.getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                pageImage.setOperation("NULL");
            }
            else{
                optionsMenu.findItem(R.id.pencilButton).getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                optionsMenu.findItem(R.id.highlighterButton).getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                optionsMenu.findItem(R.id.eraserButton).getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                item.getIcon().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_ATOP);
                pageImage.setOperation("Pencil");
            }
        }
        else if(id == R.id.highlighterButton){
            Log.d("operation", "highlighter");
            if(pageImage.getOperation().equals("Highlighter")){
                item.getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                pageImage.setOperation("NULL");
            }
            else{
                optionsMenu.findItem(R.id.pencilButton).getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                optionsMenu.findItem(R.id.highlighterButton).getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                optionsMenu.findItem(R.id.eraserButton).getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                item.getIcon().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_ATOP);
                pageImage.setOperation("Highlighter");
            }
        }
        else if(id == R.id.eraserButton){
            Log.d("operation", "eraser");
            if(pageImage.getOperation().equals("Eraser")){
                item.getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                pageImage.setOperation("NULL");
            }
            else{
                optionsMenu.findItem(R.id.pencilButton).getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                optionsMenu.findItem(R.id.highlighterButton).getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                optionsMenu.findItem(R.id.eraserButton).getIcon().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
                item.getIcon().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_ATOP);
                pageImage.setOperation("Eraser");
            }
        }
        return super.onOptionsItemSelected(item);
    }




}
