package net.kyu_mu.pigeonholeview.example;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

/**
 * Created by nao on 3/6/15.
 */
public class SelectActionDialogFragment extends DialogFragment {
    public static final String TAG = SelectActionDialogFragment.class.getSimpleName();

    private boolean isFiredEvent;

    public interface SelectActionDialogFragmentListener {
        public void onChooseEdit();
        public void onChooseDelete();
        public void onChooseCancel();
    }

    private SelectActionDialogFragmentListener SelectActionDialogFragmentListener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        isFiredEvent = false;

        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            // Verify that the host activity implements the callback interface
            try {
                SelectActionDialogFragmentListener = (SelectActionDialogFragmentListener) activity;
            } catch (ClassCastException e) {
                // The activity doesn't implement the interface, throw exception
                throw new ClassCastException(activity.toString()
                        + " must implement SelectActionDialogFragmentListener");
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final CharSequence[] items = {
                "Edit",
                "Delete",
                "Cancel",
        };

        builder.setTitle("Example actions");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                switch (item) {
                    case 0: { // Edit
                        dialog.dismiss();
                        isFiredEvent = true;
                        if (SelectActionDialogFragmentListener != null) {
                            SelectActionDialogFragmentListener.onChooseEdit();
                        }
                        break;
                    }
                    case 1: { // Delete
                        dialog.dismiss();
                        isFiredEvent = true;
                        if (SelectActionDialogFragmentListener != null) {
                            SelectActionDialogFragmentListener.onChooseDelete();
                        }
                        break;
                    }
                    case 2: { // Cancel
                        dialog.dismiss();
                        isFiredEvent = true;
                        if (SelectActionDialogFragmentListener != null) {
                            SelectActionDialogFragmentListener.onChooseCancel();
                        }
                        break;
                    }
                }
            }
        });

        return builder.create();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (!isFiredEvent && SelectActionDialogFragmentListener != null) {
            SelectActionDialogFragmentListener.onChooseCancel();
        }
    }
}
