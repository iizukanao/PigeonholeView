package net.kyu_mu.pigeonholeview;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Iterator;

/**
 * Grid-based reorderable view like Android home screen.
 *
 * Copyright (C) 2015 Nao Iizuka
 */
public class PigeonholeView<T> extends ViewGroup {
    public static final String TAG = PigeonholeView.class.getSimpleName();

    public static final int POSITION_INVALID = -1;
    public static final int POSITION_DROP_AREA = -2;

    private Context context;
    private int numColumns;
    private int numRows;
    private long dragStartAnimationDuration;
    private float topSpaceHeight; // usually this is equal to actionBarSize
    private float editDropAreaTopPadding;
    private String editDropAreaText;
    private float editDropAreaBottomPadding = 0;
    private boolean editable;
    private float cellWidth;
    private float cellHeight;
    private float widthPerCell;
    private float heightPerCell;
    private CellData<T> hoverCellData;
    private ImageView dropTargetView;
    private ImageView swapTargetView;
    private Paint textPaint;
    private SparseArray<CellData<T>> cellMap;
    private int activePointerId = -1; // MotionEvent.INVALID_POINTER_ID;
    private float lastTouchX;
    private float lastTouchY;
    private float posX;
    private float posY;
    private boolean isDragging = false;
    private PigeonholeViewListener<T> listener;
    private int editingPosition = POSITION_INVALID;
    private CellData<T> swapCandidateCellData;
    private int currentHoverPosition;
    private float paddingLeft;
    private float paddingTop;
    private float paddingRight;
    private float paddingBottom;
    private OnCellClickListener<T> onCellClickListener;
    private View editDropAreaView;
    private DataProvider<T> provider;

    public interface DataProvider<T> {
        public int getViewPosition(T item);

        public void setViewPosition(T item, int viewPosition);

        public View getView(View existingView, T item);

        public Iterator<T> iterator();
    }

    public interface OnCellClickListener<T> {
        void onClick(CellData<T> cellData);
    }

    public interface PigeonholeViewListener<T> {
        void onDragStart();

        void onDragEnd();

        void onEditObject(T object);

        void onReorder();
    }

    public static class CellData<T> {
        private View view;
        private int position;
        private T object;

        public CellData(View view, int position, T object) {
            this.view = view;
            this.position = position;
            this.object = object;
        }

        public CellData() {
        }

        public View getView() {
            return view;
        }

        public void setView(View view) {
            this.view = view;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public T getObject() {
            return object;
        }

        public void setObject(T object) {
            this.object = object;
        }
    }

    public PigeonholeView(Context context) {
        super(context);
        init(context);
    }

    public PigeonholeView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.PigeonholeView, 0, 0
        );

        // If numColumns or numRows is zero, it may cause "divide by zero" exception in getXYForPosition().
        // So initializing these variables here is necessary.
        this.numColumns = 1;
        this.numRows = 1;

        try {
            this.dragStartAnimationDuration = a.getInteger(R.styleable.PigeonholeView_dragStartAnimationDuration, 200);
            this.editable = a.getBoolean(R.styleable.PigeonholeView_editable, true);

            // a.getDimension() will return a number of pixels
            this.topSpaceHeight = a.getDimension(R.styleable.PigeonholeView_topSpaceHeight, 0);
            this.cellWidth = a.getDimension(R.styleable.PigeonholeView_cellWidth, 80);
            this.cellHeight = a.getDimension(R.styleable.PigeonholeView_cellHeight, 90);
            this.editDropAreaTopPadding = a.getDimension(R.styleable.PigeonholeView_dropAreaTopPadding, 20);
            this.editDropAreaText = a.getString(R.styleable.PigeonholeView_dropAreaText);
        } finally {
            a.recycle();
        }

        init(context);
    }

    public PigeonholeViewListener getListener() {
        return listener;
    }

    /**
     * Sets PigeonholeViewListener for this instance.
     *
     * @param listener The callback that will run
     */
    public void setListener(PigeonholeViewListener<T> listener) {
        this.listener = listener;
    }

    /**
     * Returns the number of columns.
     *
     * @return The number of columns
     */
    public int getNumColumns() {
        return numColumns;
    }

    /**
     * Sets the number of columns.
     *
     * @param numColumns The number of columns
     */
    public void setNumColumns(int numColumns) {
        this.numColumns = numColumns;
        invalidate();
        requestLayout();
    }

    /**
     * Returns the number of rows.
     *
     * @return The number of rows
     */
    public int getNumRows() {
        return numRows;
    }

    /**
     * Sets the number of rows.
     *
     * @param numRows The number of rows
     */
    public void setNumRows(int numRows) {
        this.numRows = numRows;
        invalidate();
        requestLayout();
    }

    /**
     * Returns the maximum view position for this view.
     *
     * @return The maximum view position
     */
    public int getMaxPosition() {
        return this.numColumns * this.numRows - 1;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int h = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();

        // Call measure() on every child views. Otherwise those views are invisible.
        if (cellMap != null) {
            for (int i = 0, l = cellMap.size(); i < l; i++) {
                CellData<T> cellData = cellMap.valueAt(i);
                cellData.getView().measure((int) this.widthPerCell, (int) this.heightPerCell);
            }
        }
        editDropAreaView.measure(
                (int) (this.widthPerCell * this.numColumns),
                (int) (topSpaceHeight - editDropAreaTopPadding - editDropAreaBottomPadding)
        );

        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        layoutComponents(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // This method must be overridden.
        // Do not call the superclass method here.
    }

    private void layoutComponents(int w, int h) {
        paddingLeft = paddingRight = w * .03f;
        paddingTop = h * .03f + topSpaceHeight;
        paddingBottom = h * .03f;

        float ww = (float) w - paddingLeft - paddingRight;
        float hh = (float) h - paddingTop - paddingBottom;

        this.numColumns = (int) (ww / this.cellWidth);
        this.numRows = (int) (hh / this.cellHeight);

        float horizontalReminder = ww - this.numColumns * this.cellWidth;
        paddingLeft += horizontalReminder / 2.0f;
        float verticalReminder = hh - this.numRows * this.cellHeight;
        paddingTop += verticalReminder / 2.0f;

        this.widthPerCell = this.cellWidth;
        this.heightPerCell = this.cellHeight;

        dropTargetView.layout(0, 0, (int) this.widthPerCell, (int) this.heightPerCell);
        swapTargetView.layout(0, 0, (int) this.widthPerCell, (int) this.heightPerCell);
        editDropAreaView.layout(
                (int) paddingLeft,
                (int) editDropAreaTopPadding,
                (int) (paddingLeft + this.widthPerCell * this.numColumns),
                (int) (topSpaceHeight - editDropAreaBottomPadding)
        );

        if (cellMap != null) {
            int maxPosition = getMaxPosition();

            // SparseArray version
            for (int i = 0, l = cellMap.size(); i < l; i++) {
                int position = cellMap.keyAt(i);
                if (position <= maxPosition) {
                    CellData<T> cellData = cellMap.valueAt(i);
                    if (cellData == hoverCellData) {
                        continue;
                    }
                    int row = position / numColumns;
                    int col = position % numColumns;
                    cellData.getView().layout((int) (paddingLeft + col * widthPerCell),
                            (int) (paddingTop + row * heightPerCell),
                            (int) (paddingLeft + (col + 1) * widthPerCell),
                            (int) (paddingTop + (row + 1) * heightPerCell));
                }
            }
        }
    }

    /**
     * Returns whether x,y is inside the drop area or not.
     *
     * @param x Pixels along the x-axis
     * @param y Pixels along the y-axis
     * @return True if x,y is inside the drop area. Otherwise false.
     */
    private boolean isInDropArea(float x, float y) {
        return x >= paddingLeft
                && x <= paddingLeft + widthPerCell * numColumns
                && y >= editDropAreaTopPadding
                && y <= topSpaceHeight - editDropAreaBottomPadding;
    }

    /**
     * Returns the view position for x,y.
     *
     * @param x Pixels along the x-axis
     * @param y Pixels along the y-axis
     * @return The view position for x,y
     */
    private int getPositionForXY(float x, float y) {
        if (isInDropArea(x, y)) {
            return POSITION_DROP_AREA;
        }
        if (x < paddingLeft || y < paddingTop) {
            return POSITION_INVALID;
        }
        int row = (int) ((y - paddingTop) / this.heightPerCell);
        int col = (int) ((x - paddingLeft) / this.widthPerCell);
        int position = row * this.numColumns + col;
        if (col >= this.numColumns || position >= this.numColumns * this.numRows) {
            position = POSITION_INVALID;
        }
        return position;
    }

    /**
     * Returns Point(col, row) for x,y.
     *
     * @param x Pixels along the x-axis
     * @param y Pixels along the y-axis
     * @return Point(col, row) for x,y
     */
    private Point getColRowForXY(float x, float y) {
        int position = getPositionForXY(x, y);
        if (position == POSITION_INVALID || position == POSITION_DROP_AREA) {
            return null;
        }
        int row = position / this.numColumns;
        int col = position % this.numColumns;
        return new Point(col, row);
    }

    /**
     * Returns Point(x-pixels, y-pixels) for the view position.
     *
     * @param position The view position
     * @return Point(x-pixels, y-pixels)
     */
    private Point getXYForPosition(int position) {
        int row = position / this.numColumns;
        int col = position % this.numColumns;
        int x = (int) (paddingLeft + col * this.widthPerCell);
        int y = (int) (paddingTop + row * this.heightPerCell);
        return new Point(x, y);
    }

    /**
     * Returns Point(x-pixels, y-pixels) for the column and row.
     *
     * @param col Column index (starts from zero)
     * @param row Row index (starts from zero)
     * @return Point(x-pixels, y-pixels) for the column and row
     */
    private Point getXYForColRow(int col, int row) {
        int x = (int) (paddingLeft + col * this.widthPerCell);
        int y = (int) (paddingTop + row * this.heightPerCell);
        return new Point(x, y);
    }

    /**
     * Sets DataProvider for this view.
     *
     * @param provider A DataProvider
     */
    public void setDataProvider(DataProvider<T> provider) {
        this.provider = provider;
        setupViews();
    }

    private void enableEditing() {
        if (cellMap != null) {
            // SparseArray version
            for (int i = 0, l = cellMap.size(); i < l; i++) {
                CellData<T> cellData = cellMap.valueAt(i);
                View view = cellData.getView();
                view.setLongClickable(true);
            }
        }
    }

    private void disableEditing() {
        cancelEdit();

        if (cellMap != null) {
            // SparseArray version
            for (int i = 0, l = cellMap.size(); i < l; i++) {
                CellData<T> cellData = cellMap.valueAt(i);
                View view = cellData.getView();
                view.setLongClickable(false);
            }
        }
    }

    /**
     * Enable or disable edit mode.
     *
     * @param set To enable edit mode, true. To disable, false.
     */
    public void setEditable(boolean set) {
        if (editable != set) {
            editable = set;
            if (editable) { // Enable editing
                enableEditing();
            } else { // Disable editing
                disableEditing();
            }
        }
    }

    /**
     * Creates and adds cell views using DataProvider.
     */
    private void setupViews() {
        cellMap = new SparseArray<>();
        Iterator<T> iter = this.provider.iterator();
        boolean isAltered = false;
        while (iter.hasNext()) {
            T item = iter.next();
            View cellView = this.provider.getView(null, item);
            int position = this.provider.getViewPosition(item);
            if (cellView != null && position != POSITION_INVALID) {
                // check validity of position
                if (cellMap.get(position) != null) {
                    int altPosition = getMinimumVacantPosition();
                    if (altPosition == POSITION_INVALID || altPosition == POSITION_DROP_AREA) {
                        Log.e(TAG, "No available position for overlapping position: " + position);
                        continue;
                    }
                    Log.w(TAG, "Assigned new position " + altPosition + " for overlapped position " + position);
                    this.provider.setViewPosition(item, altPosition);
                    position = altPosition;
                    isAltered = true;
                }

                addView(cellView);
                cellView.setClickable(true);
                final CellData<T> cellData = new CellData<>(cellView, position, item);
                cellView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (onCellClickListener != null) {
                            onCellClickListener.onClick(cellData);
                        }
                    }
                });
                cellView.setOnLongClickListener(new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        if (isDragging) {
                            return false;
                        } else {
                            startDrag(cellData);
                            return true;
                        }
                    }
                });
                if (!editable) {
                    cellView.setLongClickable(false);
                }
                cellMap.put(cellData.getPosition(), cellData);
            }
        }
        if (isAltered) {
            if (listener != null) {
                listener.onReorder();
            }
        }
    }

    private int getMinimumVacantPositionWithoutUpperLimit() {
        for (int i = 0; ; i++) {
            if (cellMap.get(i) == null) {
                return i;
            }
        }
    }

    private int getMinimumVacantPosition() {
        int maxPosition = this.numColumns * this.numRows - 1;
        for (int i = 0; i <= maxPosition; i++) {
            if (cellMap.get(i) == null) {
                return i;
            }
        }
        return POSITION_INVALID;
    }

    /**
     * Checks whether the matrix is full. Cells cannot be added
     * if the matrix is full.
     *
     * @return True if the matrix is full. Otherwise false.
     */
    public boolean isFull() {
        return getMinimumVacantPosition() == POSITION_INVALID;
    }

    /**
     * Adds a cell to this view. You can use this method to dynamically
     * add a cell. The view position will be automatically assigned.
     * If there is no view position available, it assigns invisible
     * (above the maximum) view position.
     *
     * @param object Object representing a cell
     */
    public void addObject(T object) {
        int position = getMinimumVacantPosition();
        if (position == -1) {
            position = getMinimumVacantPositionWithoutUpperLimit();
            Log.w(TAG, "Assigning a position above upper limit: " + position);
        }
        if (this.provider == null) {
            throw new IllegalStateException("DataProvider is null");
        }
        this.provider.setViewPosition(object, position);
        if (listener != null) {
            listener.onReorder();
        }

        View cellView = this.provider.getView(null, object);
        if (cellView != null) {
            cellView.setClickable(true);
            final CellData<T> cellData = new CellData<>(cellView, position, object);
            cellView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (onCellClickListener != null) {
                        onCellClickListener.onClick(cellData);
                    }
                }
            });
            cellView.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    if (isDragging) {
                        return false;
                    } else {
                        startDrag(cellData);
                        return true;
                    }
                }
            });
            if (!editable) {
                cellView.setLongClickable(false);
            }
            cellMap.put(position, cellData);
            addView(cellView);
            if (position <= getMaxPosition()) {
                Point cellPoint = getXYForPosition(position);
                cellView.measure((int) widthPerCell, (int) heightPerCell);
                cellView.layout(
                        cellPoint.x,
                        cellPoint.y,
                        (int) (cellPoint.x + widthPerCell),
                        (int) (cellPoint.y + heightPerCell)
                );
                invalidate();
            }
        }
    }

    /**
     * Removes a cell from this view.
     *
     * @param cellData CellData that will be removed
     */
    private void deleteCell(CellData<T> cellData) {
        if (cellData == null) {
            Log.e(TAG, "deleteCell: cellData is null");
            return;
        }
        cellMap.remove(cellData.getPosition());

        if (listener != null) {
            listener.onReorder();
        }

        final View cellView = cellData.getView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            AnimatorSet animatorSet = new AnimatorSet();

            // shrink
            ObjectAnimator animScaleX = ObjectAnimator.ofFloat(cellView, "scaleX", 0f);
            ObjectAnimator animScaleY = ObjectAnimator.ofFloat(cellView, "scaleY", 0f);

            // fade out
            ObjectAnimator animFadeOut = ObjectAnimator.ofFloat(cellView, "alpha", 1f, 0f);
            animatorSet.playTogether(animScaleX, animScaleY, animFadeOut);
            animatorSet.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    removeView(cellView);
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }
            });
            animatorSet.start();
        } else {
            removeView(cellView);
        }
    }

    private void deleteHoveringObject() {
        deleteCell(hoverCellData);
    }

    private void editHoveringObject() {
        if (hoverCellData != null) {
            editingPosition = hoverCellData.getPosition();
            T cellInfo = hoverCellData.getObject();
            if (listener != null) {
                listener.onEditObject(cellInfo);
            }
        }
    }

    /**
     * Removes the currently editing cell from this view.
     */
    public void deleteEditingObject() {
        CellData<T> cellData = cellMap.get(editingPosition);
        if (cellData != null) {
            deleteCell(cellData);
        }

        if (listener != null) {
            listener.onDragEnd();
        }
    }

    private void putBackEditingCellView() {
        if (editingPosition != POSITION_INVALID) {
            CellData<T> cellData = cellMap.get(editingPosition);
            if (cellData != null) {
                View cellView = cellData.getView();

                Point destXY = getXYForPosition(editingPosition);
                if (destXY != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        cellView.setScaleX(1.0f);
                        cellView.setScaleY(1.0f);
                        cellView.setX(destXY.x);
                        cellView.setY(destXY.y);

                        // Fade in the cell
                        AnimatorSet animatorSet = new AnimatorSet();
                        ObjectAnimator animFadeIn = ObjectAnimator.ofFloat(cellView, "alpha", 0f, 1f);
                        animatorSet.play(animFadeIn);
                        animatorSet.start();
                    } else {
                        cellView.layout(
                                destXY.x,
                                destXY.y,
                                (int) (destXY.x + widthPerCell),
                                (int) (destXY.y + heightPerCell)
                        );
                    }
                    invalidate();
                }
            }
            editingPosition = POSITION_INVALID;
        }
    }

    /**
     * Cancels the ongoing edit.
     */
    public void cancelEdit() {
        putBackEditingCellView();

        if (listener != null) {
            listener.onDragEnd();
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putInt("editingPosition", editingPosition);
        bundle.putParcelable("superState", super.onSaveInstanceState());
        return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            editingPosition = bundle.getInt("editingPosition", POSITION_INVALID);
            Parcelable superState = bundle.getParcelable("superState");
            super.onRestoreInstanceState(superState);
        } else {
            Log.e(TAG, "state is not an instance of Bundle");
        }
    }

    /**
     * Updates the view using DataProvider.
     */
    public void notifyDataSetChanged() {
        invalidate();
    }

    /**
     * Updates the view for the cell which is being edited.
     */
    public void updateEditingObject() {
        if (editingPosition != POSITION_INVALID) {
            CellData<T> cellData = cellMap.get(editingPosition);
            if (cellData != null) {
                if (this.provider == null) {
                    throw new IllegalStateException("DataProvider is null");
                }

                // Update contents of the view
                this.provider.getView(cellData.getView(), cellData.getObject());

                invalidate();
            } else {
                Log.e(TAG, "No data at editingPosition");
            }
        }

        putBackEditingCellView();

        if (listener != null) {
            listener.onDragEnd();
        }
    }

    /**
     * Moves the cell to the new position.
     *
     * @param cellData    CellData that will be moved
     * @param newPosition The new position for the cell
     */
    private void moveCell(CellData<T> cellData, int newPosition) {
        Point newPoint = getXYForPosition(newPosition);
        if (newPoint == null) {
            Log.e(TAG, "moveCell: Invalid position is specified: " + newPosition);
            return;
        }

        // Move the cell to newPosition
        View cellView = cellData.getView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            AnimatorSet animatorSet = new AnimatorSet();
            ObjectAnimator animX = ObjectAnimator.ofFloat(cellView, "x", newPoint.x);
            ObjectAnimator animY = ObjectAnimator.ofFloat(cellView, "y", newPoint.y);
            animatorSet.playTogether(animX, animY);
            animatorSet.start();
        } else {
            cellView.layout(
                    newPoint.x,
                    newPoint.y,
                    (int) (newPoint.x + widthPerCell),
                    (int) (newPoint.y + heightPerCell)
            );
        }

        int oldPosition = cellData.getPosition();
        cellData.setPosition(newPosition);
        cellMap.remove(oldPosition);
        cellMap.put(newPosition, cellData);

        T object = cellData.getObject();
        if (this.provider == null) {
            throw new IllegalStateException("DataProvider is null");
        }
        this.provider.setViewPosition(object, newPosition);
    }

    private void cancelMove() {
        if (hoverCellData == null) {
            Log.e(TAG, "cancelMove: hoverCellData is null");
            return;
        }

        // Move and back to normal scale
        Point targetPoint = getXYForPosition(hoverCellData.getPosition());
        View cellView = hoverCellData.getView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            AnimatorSet animatorSet = new AnimatorSet();
            ObjectAnimator animX = ObjectAnimator.ofFloat(cellView, "x", targetPoint.x);
            ObjectAnimator animY = ObjectAnimator.ofFloat(cellView, "y", targetPoint.y);
            ObjectAnimator animScaleX = ObjectAnimator.ofFloat(cellView, "scaleX", 1.0f);
            ObjectAnimator animScaleY = ObjectAnimator.ofFloat(cellView, "scaleY", 1.0f);
            animatorSet.playTogether(animX, animY, animScaleX, animScaleY);
            animatorSet.start();
        } else {
            cellView.layout(
                    targetPoint.x,
                    targetPoint.y,
                    (int) (targetPoint.x + widthPerCell),
                    (int) (targetPoint.y + heightPerCell)
            );
        }

        swapTargetView.setVisibility(View.GONE);
    }

    private void endDrag(float x, float y) {
        if (hoverCellData == null) {
            Log.e(TAG, "endDrag: hoverCellData is null");
            return;
        }
        int dropPosition = getPositionForXY(x, y);
        boolean isAltered = false;
        if (dropPosition == POSITION_INVALID) { // Cancel move
            cancelMove();
            if (listener != null) {
                listener.onDragEnd();
            }
        } else if (dropPosition == POSITION_DROP_AREA) { // Edit object
            editHoveringObject();
        } else { // Move
            Point dropPoint = getColRowForXY(x, y);
            Point dropXY = getXYForColRow(dropPoint.x, dropPoint.y);
            View cellView = hoverCellData.getView();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                // Move and shrink
                AnimatorSet animatorSet = new AnimatorSet();
                ObjectAnimator animX = ObjectAnimator.ofFloat(cellView, "x", dropXY.x);
                ObjectAnimator animY = ObjectAnimator.ofFloat(cellView, "y", dropXY.y);
                ObjectAnimator animScaleX = ObjectAnimator.ofFloat(cellView, "scaleX", 1.0f);
                ObjectAnimator animScaleY = ObjectAnimator.ofFloat(cellView, "scaleY", 1.0f);
                animatorSet.playTogether(animX, animY, animScaleX, animScaleY);
                animatorSet.start();
            } else {
                cellView.layout(
                        dropXY.x,
                        dropXY.y,
                        (int) (dropXY.x + widthPerCell),
                        (int) (dropXY.y + heightPerCell)
                );
            }

            int oldPosition = hoverCellData.getPosition();
            int newPosition = getPositionForXY(x, y);
            if (newPosition != oldPosition) {
                hoverCellData.setPosition(newPosition);
                cellMap.remove(oldPosition);
                if (swapCandidateCellData != null && swapCandidateCellData != hoverCellData) {
                    moveCell(swapCandidateCellData, oldPosition);
                    swapTargetView.setVisibility(View.GONE);
                }
                cellMap.put(newPosition, hoverCellData);
                // Moved cell from oldPosition to newPosition

                T object = hoverCellData.getObject();
                if (this.provider == null) {
                    throw new IllegalStateException("DataProvider is null");
                }
                this.provider.setViewPosition(object, newPosition);

                isAltered = true;
            }
            if (listener != null) {
                listener.onDragEnd();
            }
        }
        dropTargetView.setVisibility(View.GONE);

        if (isAltered) {
            if (listener != null) {
                listener.onReorder();
            }
        }
    }

    /**
     * Starts dragging the cell.
     *
     * @param cellData CellData that is started dragging
     */
    private void startDrag(CellData<T> cellData) {
        if (isDragging) { // Another cell is being dragged
            return;
        }

        if (listener != null) {
            listener.onDragStart();
        }

        View cellView = cellData.getView();
        hoverCellData = cellData;

        float newX;
        float newY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            newX = cellView.getX();
            newY = cellView.getY();
        } else {
            newX = cellView.getLeft();
            newY = cellView.getTop();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // Zoom
            AnimatorSet animatorSet = new AnimatorSet();
            ObjectAnimator animX = ObjectAnimator.ofFloat(cellView, "x", newX);
            ObjectAnimator animY = ObjectAnimator.ofFloat(cellView, "y", newY);
            ObjectAnimator animScaleX = ObjectAnimator.ofFloat(cellView, "scaleX", 1.25f);
            ObjectAnimator animScaleY = ObjectAnimator.ofFloat(cellView, "scaleY", 1.25f);
            animatorSet.setDuration(dragStartAnimationDuration);
            animatorSet.playTogether(animX, animY, animScaleX, animScaleY);
            animatorSet.start();
        } else {
            cellView.layout(
                    (int) newX,
                    (int) newY,
                    (int) (newX + widthPerCell),
                    (int) (newY + heightPerCell)
            );
        }

        posX = newX;
        posY = newY;
        currentHoverPosition = cellData.getPosition();
        isDragging = true;
        cellView.bringToFront();

        invalidate();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                try {
                    final int pointerIndex = MotionEventCompat.getActionIndex(ev);
                    final float x = MotionEventCompat.getX(ev, pointerIndex);
                    final float y = MotionEventCompat.getY(ev, pointerIndex);

                    // Remember where we started (for dragging)
                    lastTouchX = x;
                    lastTouchY = y;
                    // Save the ID of this pointer (for dragging)
                    activePointerId = MotionEventCompat.getPointerId(ev, 0);
                } catch (IllegalArgumentException ex) { // pointerIndex out of range
                    Log.e(TAG, "IllegalArgumentException: " + ex.getMessage());
                    return false;
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                onMouseActionUp(ev);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                cancelDrag();
                break;
            }
        }

        return isDragging;
    }

    private void resetDragState() {
        hoverCellData = null;
        swapCandidateCellData = null;
        currentHoverPosition = POSITION_INVALID;
        activePointerId = -1; // MotionEvent.INVALID_POINTER_ID;
    }

    private void cancelDrag() {
        if (isDragging) {
            cancelMove();
            if (listener != null) {
                listener.onDragEnd();
            }
            dropTargetView.setVisibility(View.GONE);
            isDragging = false;
        }
        resetDragState();
    }

    private void onMouseActionUp(MotionEvent ev) {
        if (isDragging) {
            isDragging = false;
            try {
                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float y = MotionEventCompat.getY(ev, pointerIndex);
                endDrag(x, y);
            } catch (IllegalArgumentException ex) { // pointerIndex out of range
                Log.e(TAG, "IllegalArgumentException: " + ex.getMessage());
            }
        }
        resetDragState();
    }

    private void cancelSwapCandidate() {
        if (swapCandidateCellData == null) {
            return;
        }
        if (swapCandidateCellData == hoverCellData) {
            return;
        }

        View swapCandidateView = swapCandidateCellData.getView();
        Point targetPoint = getXYForPosition(swapCandidateCellData.getPosition());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // Move the swap candidate cell back to its original position
            AnimatorSet animatorSet = new AnimatorSet();
            ObjectAnimator animX = ObjectAnimator.ofFloat(swapCandidateView, "x", targetPoint.x);
            ObjectAnimator animY = ObjectAnimator.ofFloat(swapCandidateView, "y", targetPoint.y);
            animatorSet.playTogether(animX, animY);
            animatorSet.start();
        } else {
            swapCandidateView.layout(
                    targetPoint.x,
                    targetPoint.y,
                    (int) (targetPoint.x + widthPerCell),
                    (int) (targetPoint.y + heightPerCell)
            );
        }

        swapTargetView.setVisibility(View.GONE);
    }

    private void swapCandidateEffect() {
        if (swapCandidateCellData == null || hoverCellData == null) {
            return;
        }
        if (swapCandidateCellData == hoverCellData) {
            return;
        }

        // Move the swap candidate cell to the original position of hovering cell
        View swapCandidateView = swapCandidateCellData.getView();
        swapCandidateView.bringToFront();
        hoverCellData.getView().bringToFront();
        Point targetPoint = getXYForPosition(hoverCellData.getPosition());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            swapTargetView.setX(targetPoint.x);
            swapTargetView.setY(targetPoint.y);
        } else {
            swapTargetView.layout(
                    targetPoint.x,
                    targetPoint.y,
                    (int) (targetPoint.x + widthPerCell),
                    (int) (targetPoint.y + heightPerCell)
            );
        }
        swapTargetView.setVisibility(View.VISIBLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            AnimatorSet animatorSet = new AnimatorSet();
            ObjectAnimator animX = ObjectAnimator.ofFloat(swapCandidateView, "x", targetPoint.x);
            ObjectAnimator animY = ObjectAnimator.ofFloat(swapCandidateView, "y", targetPoint.y);
            animatorSet.playTogether(animX, animY);
            animatorSet.start();
        } else {
            swapCandidateView.layout(
                    targetPoint.x,
                    targetPoint.y,
                    (int) (targetPoint.x + widthPerCell),
                    (int) (targetPoint.y + heightPerCell)
            );
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                try {
                    final int pointerIndex = MotionEventCompat.getActionIndex(ev);
                    final float x = MotionEventCompat.getX(ev, pointerIndex);
                    final float y = MotionEventCompat.getY(ev, pointerIndex);

                    // Remember where we started (for dragging)
                    lastTouchX = x;
                    lastTouchY = y;
                    // Save the ID of this pointer (for dragging)
                    activePointerId = MotionEventCompat.getPointerId(ev, 0);
                } catch (IllegalArgumentException ex) { // pointerIndex out of range
                    Log.e(TAG, "IllegalArgumentException: " + ex.getMessage());
                    return false;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    try {
                        final int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
                        final float x = MotionEventCompat.getX(ev, pointerIndex);
                        final float y = MotionEventCompat.getY(ev, pointerIndex);

                        // Calculate the distance moved
                        final float dx = x - lastTouchX;
                        final float dy = y - lastTouchY;

                        posX += dx;
                        posY += dy;
                        if (hoverCellData != null) { // User is dragging a cell
                            View cellView = hoverCellData.getView();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                                cellView.setX(posX);
                                cellView.setY(posY);
                            } else {
                                cellView.layout((int) posX, (int) posY, (int) (posX + widthPerCell), (int) (posY + heightPerCell));
                            }
                            int hoverPosition = getPositionForXY(x, y);
                            if (hoverPosition != currentHoverPosition) {
                                cancelSwapCandidate();
                                swapCandidateCellData = null;
                            }
                            if (hoverPosition == POSITION_DROP_AREA) {
                                editDropAreaView.setBackgroundResource(R.drawable.drop_area_highlight);
                            } else {
                                editDropAreaView.setBackgroundResource(R.drawable.drop_area);
                            }
                            Point hoverPoint = getColRowForXY(x, y);
                            if (hoverPoint != null) {
                                // Show the drop target
                                Point hoverXY = getXYForColRow(hoverPoint.x, hoverPoint.y);
                                dropTargetView.setVisibility(View.VISIBLE);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                                    dropTargetView.setX(hoverXY.x);
                                    dropTargetView.setY(hoverXY.y);
                                } else {
                                    dropTargetView.layout(
                                            hoverXY.x,
                                            hoverXY.y,
                                            (int) (hoverXY.x + widthPerCell),
                                            (int) (hoverXY.y + heightPerCell)
                                    );
                                }

                                CellData<T> dropTargetCellData = cellMap.get(hoverPosition);
                                if (dropTargetCellData != null) {
                                    if (hoverPosition != currentHoverPosition) {
                                        // Move the drop target cell
                                        swapCandidateCellData = dropTargetCellData;
                                        swapCandidateEffect();
                                    }
                                }
                            } else {
                                // Hide the drop target
                                dropTargetView.setVisibility(View.GONE);
                            }
                            currentHoverPosition = hoverPosition;
                        }

                        invalidate(); // TODO: Is this necessary?

                        // Remember this touch position for the next move event
                        lastTouchX = x;
                        lastTouchY = y;
                    } catch (IllegalArgumentException ex) { // pointerIndex out of range
                        Log.e(TAG, "IllegalArgumentException: " + ex.getMessage());
                        return false;
                    }
//                } else {
//                    // First pointer down -> second pointer down -> first pointer up -> second pointer drag -> (here)
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                onMouseActionUp(ev);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                cancelDrag();
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = MotionEventCompat.getActionIndex(ev);
                final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);

                if (pointerId == activePointerId) {
                    onMouseActionUp(ev);
                }
                break;
            }
        }
        return true;
    }

    /**
     * Initialize this view.
     *
     * @param context
     */
    private void init(Context context) {
        this.context = context;

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(60);

        dropTargetView = new ImageView(context);
        dropTargetView.setImageResource(R.drawable.placeholder);
        dropTargetView.setScaleType(ImageView.ScaleType.FIT_XY);
        dropTargetView.setVisibility(View.GONE);
        addView(dropTargetView);

        swapTargetView = new ImageView(context);
        swapTargetView.setImageResource(R.drawable.swap_candidate);
        swapTargetView.setScaleType(ImageView.ScaleType.FIT_XY);
        swapTargetView.setVisibility(View.GONE);
        addView(swapTargetView);

        LayoutInflater inflater = LayoutInflater.from(context);

        // Drop area
        editDropAreaView = inflater.inflate(R.layout.drop_area, this, false);
        addView(editDropAreaView);

        if (editDropAreaText != null) {  // Use custom text for drop area
            TextView dropAreaTextView = (TextView) editDropAreaView.findViewById(R.id.drop_area__label);
            dropAreaTextView.setText(editDropAreaText);
        }
    }

    public OnCellClickListener getOnCellClickListener() {
        return onCellClickListener;
    }

    /**
     * Set the listener that will be called when user clicks a cell
     *
     * @param onCellClickListener The callback that will run
     */
    public void setOnCellClickListener(OnCellClickListener<T> onCellClickListener) {
        this.onCellClickListener = onCellClickListener;
    }
}
