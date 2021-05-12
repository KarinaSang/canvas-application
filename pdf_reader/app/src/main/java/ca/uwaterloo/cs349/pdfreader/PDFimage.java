package ca.uwaterloo.cs349.pdfreader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Stack;

@SuppressLint("AppCompatCustomView")
public class PDFimage extends ImageView{


    final String LOGNAME = "pdf_image";

    private ScaleGestureDetector scaleDetector;
    private float scaleFactor = 1.f;
    float oldDist = 1f;

    private int pageNumber = 0;

    // drawing path
    Path path;
    ArrayList<ArrayList<Path>> pencilPaths = new ArrayList();
    ArrayList<ArrayList<Path>> highlightPaths = new ArrayList();

    //undo && redo
    LinkedList<Pair> undoStack = new LinkedList();
    LinkedList <Pair> redoStack = new LinkedList();


    // image to display
    Bitmap bitmap;
    Paint pencilPaint = new Paint(Color.WHITE);
    Paint highlighterPaint = new Paint(Color.WHITE);
    String operation;

    private static final int INVALID_POINTER_ID = -1;

    private float mPosX;
    private float mPosY;

    //zooming and panning
    private float mLastTouchX;
    private float mLastTouchY;
    private float mLastGestureX;
    private float mLastGestureY;
    private int mActivePointerId = INVALID_POINTER_ID;


    // constructor
    public PDFimage(Context context) {
        super(context);

        operation = "";

        path = new Path();

      //  this.setOnTouchListener(new MyScaleGestures(context));

        for(int i = 0; i < 55; ++i){
            pencilPaths.add(new ArrayList<Path>());
            highlightPaths.add(new ArrayList<Path>());
        }

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }


    void undo(){
        if(!undoStack.isEmpty()){
            Pair action = undoStack.pollLast();

            if (action.first.equals("erasePencil")) {
                pencilPaths.get(pageNumber).add((Path)action.second);
                redoStack.add(new Pair("pencil", action.second));
            }
            else if(action.first.equals("eraseHighlight")){
                highlightPaths.get(pageNumber).add((Path)action.second);
                redoStack.add(new Pair("highlight", action.second));
            }
            else if(action.first.equals("pencil")){
                if(pencilPaths.get(pageNumber).contains(action.second)){
                    pencilPaths.get(pageNumber).remove(action.second);
                }
                redoStack.add(new Pair("erasePencil", action.second));
            }
            else if(action.first.equals("highlight")){
                if(highlightPaths.get(pageNumber).contains(action.second)){
                    highlightPaths.get(pageNumber).remove(action.second);
                }
                redoStack.add(new Pair("eraseHighlight", action.second));
            }
                invalidate();
        }

        if(undoStack.size() > 20){
            undoStack.removeFirst();
        }


    }

    void redo(){
        if(!redoStack.isEmpty()){
            Pair action = redoStack.pollLast();

            if (action.first.equals("erasePencil")) {
                pencilPaths.get(pageNumber).add((Path)action.second);
                undoStack.add(new Pair("pencil", action.second));
            }
            else if(action.first.equals("eraseHighlight")){
                highlightPaths.get(pageNumber).add((Path)action.second);
                undoStack.add(new Pair("highlight", action.second));
            }
            else if(action.first.equals("pencil")){
                if(pencilPaths.get(pageNumber).contains(action.second)){
                    pencilPaths.get(pageNumber).remove(action.second);
                }
                undoStack.add(new Pair("erasePencil", action.second));
            }
            else if(action.first.equals("highlight")){
                if(highlightPaths.get(pageNumber).contains(action.second)){
                    highlightPaths.get(pageNumber).remove(action.second);
                }
                undoStack.add(new Pair("eraseHighlight", action.second));
            }
                invalidate();
        }

        if(redoStack.size() > 20){
            redoStack.removeFirst();
        }


    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void nextPage(){
            if (pageNumber < 54) {
                ++pageNumber;
                scaleFactor = 1.0f;
                MainActivity.showPage(pageNumber);
                mPosX = 0;
                mPosY = 0;
                undoStack.clear();
                redoStack.clear();
                invalidate();
            }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void previousPage(){
        if (pageNumber > 0) {
            --pageNumber;
            scaleFactor = 1.0f;
            mPosX = 0;
            mPosY = 0;
            undoStack.clear();
            redoStack.clear();
            MainActivity.showPage(pageNumber);
            invalidate();
        }
    }



    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;
    private void touch_start(float x, float y) {
        path = new Path();
        path.moveTo(x, y);
        mX = x;
        mY = y;
    }
    private void touch_move(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            path.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
            mX = x;
            mY = y;
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void touch_up() {
        path.lineTo(mX, mY);

            if (operation.equals("Pencil")) {
                pencilPaths.get(pageNumber).add(path);
                undoStack.add(new Pair("pencil", path));
            } else if (operation.equals("Highlighter")) {
                highlightPaths.get(pageNumber).add(path);
                undoStack.add(new Pair("highlight", path));
            } else if (operation.equals("Eraser")) {
                //should not execute this line
            } else {
                //do nothing
            }


        path = new Path();
    }



    private void erase(){
        int temp = -1;
        ArrayList <Path> tempPencilPath = pencilPaths.get(pageNumber);
        ArrayList <Path> tempHighlighterPath = highlightPaths.get(pageNumber);

        //check pencil
        for(int i = 0; i < tempPencilPath.size(); ++i){
            RectF rectF = new RectF();
            Path p = tempPencilPath.get(i);
            p.computeBounds(rectF, true);
            Region r = new Region();
            r.setPath(p, new Region((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom));

            if(r.contains((int)mX,(int) mY)){
                temp = i;
                Log.d("operation", "Erasing");
            }
            else {
                Log.d("operation", "Not Erasing");
            }
        }

        if(temp != -1){
            Path tempPath = pencilPaths.get(pageNumber).remove(temp);
            undoStack.add(new Pair("erasePencil", tempPath));
        }
        else{
            //check highlight
            for(int i = 0; i < tempHighlighterPath.size(); ++i){
                RectF rectF = new RectF();
                Path p = tempHighlighterPath.get(i);
                p.computeBounds(rectF, false);
                Region r = new Region();
                r.setPath(p, new Region((int) rectF.left-5, (int) rectF.top-5, (int) rectF.right+5, (int) rectF.bottom-5));

                if(r.contains((int)mX,(int) mY)){
                    temp = i;
                    Log.d("operation", "Erasing");
                }
                else {
                    Log.d("operation", "Not Erasing");
                }
            }

            if(temp != -1){
                Path tempPath = highlightPaths.get(pageNumber).remove(temp);
                undoStack.add(new Pair ("eraseHighlight", tempPath));
            }
        }


    }



    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
  //      this.setScaleType(ScaleType.CENTER);

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!scaleDetector.isInProgress()) {
                    mLastTouchX = x;
                    mLastTouchY = y;
                    mActivePointerId = event.getPointerId(0);
                }

                touch_start(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (scaleDetector.isInProgress()) {
                    final float gx = scaleDetector.getFocusX();
                    final float gy = scaleDetector.getFocusY();
                    mLastGestureX = gx;
                    mLastGestureY = gy;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    final int pointerIndex = event.findPointerIndex(mActivePointerId);
                    final float xx = event.getX(pointerIndex);
                    final float yy = event.getY(pointerIndex);

                    final float dx = xx - mLastTouchX;
                    final float dy = yy - mLastTouchY;

                    mPosX += dx;
                    mPosY += dy;

                    invalidate();

                    mLastTouchX = xx;
                    mLastTouchY = yy;
                    touch_move(x, y);
                }
                else{
                    final float gx = scaleDetector.getFocusX();
                    final float gy = scaleDetector.getFocusY();

                    final float gdx = gx - mLastGestureX;
                    final float gdy = gy - mLastGestureY;

                    mPosX += gdx;
                    mPosY += gdy;

                    invalidate();

                    mLastGestureX = gx;
                    mLastGestureY = gy;
                }
                break;
            case MotionEvent.ACTION_UP:
                mActivePointerId = INVALID_POINTER_ID;
                if(operation.equals("Eraser")){
                    erase();
                }
                else{
                    if(operation.equals("Pencil")){
                        pencilPaint.setColor(Color.BLUE);
                    }
                    else if(operation.equals("Highlighter")){
                        highlighterPaint.setColor(Color.parseColor("#ffdf65"));
                    }

                    else{

                    }
                    touch_up();
                }

                invalidate();
                break;
            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
                        >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    Log.d("DEBUG", "mActivePointerId");
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mLastTouchX = event.getX(newPointerIndex);
                    mLastTouchY = event.getY(newPointerIndex);
                    mActivePointerId = event.getPointerId(newPointerIndex);
                }

                break;
            }
        }

        return true;
    }


    private float spacing(MotionEvent event)
    {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }


    // set image as background
    public void setImage(Bitmap bitmap) {
        this.bitmap = bitmap;
    }


    //set operation
    public void setOperation(String operation){
        this.operation = operation;
    }

    public String getOperation(){
        return operation;
    }

    public int getPageNumber(){
        return pageNumber;
    }

    @Override
    protected void onDraw(Canvas canvas) {

//        // draw background
        canvas.save();

        if(operation.equals("NULL") || operation.equals("")){
            canvas.translate(mPosX, mPosY);
        }
        if (scaleDetector.isInProgress()) {
            canvas.scale(scaleFactor, scaleFactor, scaleDetector.getFocusX(), scaleDetector.getFocusY());
        }
        else{
            canvas.scale(scaleFactor, scaleFactor);
        }

        for(Path path : pencilPaths.get(pageNumber)){
            pencilPaint.setStrokeWidth(5);
            pencilPaint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, pencilPaint);
        }

        for(Path path : highlightPaths.get(pageNumber)){
            highlighterPaint.setStrokeWidth(20);
            highlighterPaint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, highlighterPaint);
        }

        if (bitmap != null) {
            //resize pdf
            Rect rectangle = new Rect(0,0,(int)(bitmap.getWidth() * 2.8f), (int)(bitmap.getHeight()*2.8f));
            canvas.drawBitmap(bitmap, null, rectangle, null);
        }

        canvas.restore();

        super.onDraw(canvas);

    }

    private class ScaleListener extends
            ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 10.0f));
            Log.d("Operation", "scaling");
            invalidate();
            return true;
        }
    }


}
