package net.kyu_mu.pigeonholeview.example;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import net.kyu_mu.pigeonholeview.PigeonholeView;

import java.util.Iterator;


public class MainActivity extends ActionBarActivity implements PigeonholeView.PigeonholeViewListener<MyData>, SelectActionDialogFragment.SelectActionDialogFragmentListener {
    public static final String TAG = MainActivity.class.getSimpleName();

    private PigeonholeView<MyData> pigeonholeView;
    private MyDataList myDataList;
    private MyData editingMyData;
    private boolean isEditable;

    /**
     * Save UI state
     *
     * @param outState
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable("editingMyData", editingMyData);
        outState.putParcelable("myDataList", myDataList);
        outState.putBoolean("isEditable", isEditable);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            editingMyData = savedInstanceState.getParcelable("editingMyData");
            myDataList = savedInstanceState.getParcelable("myDataList");
            isEditable = savedInstanceState.getBoolean("isEditable");
        } else {
            myDataList = new MyDataList();
            myDataList.add(new MyData("Button 1", android.R.drawable.ic_menu_myplaces, 0));
            myDataList.add(new MyData("Button 2", android.R.drawable.ic_menu_rotate, 1));
            myDataList.add(new MyData("Button 3", android.R.drawable.ic_menu_mapmode, 2));
            isEditable = true;
        }

        pigeonholeView = (PigeonholeView<MyData>) findViewById(R.id.example_pigeonhole_view);

        PigeonholeView.DataProvider<MyData> provider = new PigeonholeView.DataProvider<MyData>() {
            @Override
            public int getViewPosition(MyData item) {
                return item.getViewPosition();
            }

            @Override
            public void setViewPosition(MyData item, int viewPosition) {
                item.setViewPosition(viewPosition);
            }

            @Override
            public View getView(View existingView, MyData signal) {
                if (existingView == null) {
                    LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                    existingView = inflater.inflate(R.layout.list_item_signal, pigeonholeView, false);
                }
                ImageView imageView = (ImageView) existingView.findViewById(R.id.grid_signal_image);
                imageView.setImageResource(signal.getImageResourceId());
                TextView nameTextView = (TextView) existingView.findViewById(R.id.grid_signal_name);
                nameTextView.setText(signal.getName());
                return existingView;
            }

            @Override
            public Iterator<MyData> iterator() {
                return myDataList.iterator();
            }
        };
        pigeonholeView.setDataProvider(provider);

        pigeonholeView.setListener(this);
        pigeonholeView.setOnCellClickListener(new PigeonholeView.OnCellClickListener<MyData>() {
            @Override
            public void onClick(PigeonholeView.CellData<MyData> cellData) {
                MyData myData = cellData.getObject();
                Toast.makeText(MainActivity.this, myData.getName() + " is clicked", Toast.LENGTH_SHORT).show();
            }
        });

        updateEditMode();
    }

    private void updateEditMode() {
        pigeonholeView.setEditable(isEditable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.findItem(R.id.action_toggle_edit).setChecked(isEditable);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_add) { // Add a new button to PigeonholeView
            if (pigeonholeView.isFull()) {
                Toast.makeText(this, "Can't add more buttons", Toast.LENGTH_SHORT).show();
            } else {
                MyData newData = new MyData("New Button", android.R.drawable.ic_menu_zoom, 0);
                myDataList.add(newData);
                pigeonholeView.addObject(newData);
            }
            return true;
        } else if (id == R.id.action_toggle_edit) { // Toggle edit mode
            isEditable = !item.isChecked();
            item.setChecked(isEditable);
            updateEditMode();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void hideActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    private void showActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.show();
        }
    }

    /**
     * Called when the dragging has started.
     */
    @Override
    public void onDragStart() {
        hideActionBar();
    }

    /**
     * Called when the dragging has ended.
     */
    @Override
    public void onDragEnd() {
        showActionBar();
    }

    /**
     * Called when the user dropped a button to the drop area.
     *
     * @param myData
     */
    @Override
    public void onEditObject(MyData myData) {
        editingMyData = myData;

        // In general, you might want to start an activity here.
        SelectActionDialogFragment dialog = new SelectActionDialogFragment();
        dialog.show(getSupportFragmentManager(), "SelectActionDialogFragment");
    }

    /**
     * Called when reordering has happened in the PigeonholeView.
     * You should save new view positions to a storage here.
     */
    @Override
    public void onReorder() {
        // TODO: Save myDataList to storage.
    }

    /**
     * Called when the user clicked "Edit" in the dialog.
     */
    @Override
    public void onChooseEdit() {
        if (editingMyData != null) {
            editingMyData.setName("<Edited>");
            editingMyData.setImageResourceId(android.R.drawable.ic_menu_edit);
            pigeonholeView.updateEditingObject();
        }
    }

    /**
     * Called when the user clicked "Delete" in the dialog.
     */
    @Override
    public void onChooseDelete() {
        if (editingMyData != null) {
            myDataList.remove(editingMyData);
            pigeonholeView.deleteEditingObject();
        }
    }

    /**
     * Called when the user clicked "Cancel" in the dialog.
     */
    @Override
    public void onChooseCancel() {
        pigeonholeView.cancelEdit();
    }
}
