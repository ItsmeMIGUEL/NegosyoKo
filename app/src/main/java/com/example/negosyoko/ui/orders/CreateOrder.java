package com.example.negosyoko.ui.orders;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;

import com.example.negosyoko.R;
import com.example.negosyoko.databinding.CreateOrderBinding;

public class CreateOrder {
    private final Context context;
    private CreateOrderBinding binding;
    private AlertDialog dialog;
    private String selectedPaymentMethod = "Cash"; // Default value

    public CreateOrder(Context context) {
        this.context = context;
    }

    public void show() {
        binding = CreateOrderBinding.inflate(LayoutInflater.from(context));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(binding.getRoot());
        builder.setCancelable(true);

        dialog = builder.create();
        dialog.show();

        setupListeners();
        setupPaymentMethodSelection();
    }

    private void setupPaymentMethodSelection() {
        binding.rgPaymentMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_cash) {
                selectedPaymentMethod = "Cash";
            } else if (checkedId == R.id.rb_gcash) {
                selectedPaymentMethod = "GCash";
            }
        });
    }

    private void setupListeners() {
        binding.btnScanSku.setOnClickListener(v -> {
            Intent scannerIntent = new Intent(context, Scanner.class);
            context.startActivity(scannerIntent);
            dismiss();
        });
    }

    public String getSelectedPaymentMethod() {
        return selectedPaymentMethod;
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}