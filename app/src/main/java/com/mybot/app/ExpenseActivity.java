package com.mybot.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExpenseActivity extends AppCompatActivity {

    private ExpenseDbHelper dbHelper;
    private ListView listView;
    private ExpenseAdapter adapter;
    private String currentFilter = null;
    private Spinner categorySpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbHelper = new ExpenseDbHelper(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("消費紀錄");
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        title.setLayoutParams(titleParams);

        Button addBtn = new Button(this);
        addBtn.setText("+ 新增");
        addBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, AddExpenseActivity.class));
        });

        header.addView(title);
        header.addView(addBtn);

        // Category filter
        categorySpinner = new Spinner(this);
        categorySpinner.setPadding(0, 16, 0, 16);
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                currentFilter = "全部".equals(selected) ? null : selected;
                refreshList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // List
        listView = new ListView(this);
        adapter = new ExpenseAdapter();
        listView.setAdapter(adapter);

        root.addView(header);
        root.addView(categorySpinner);
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCategories();
        refreshList();
    }

    private void refreshCategories() {
        List<String> cats = dbHelper.getCategories();
        List<String> items = new ArrayList<>();
        items.add("全部");
        items.addAll(cats);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);
    }

    private void refreshList() {
        adapter.data = dbHelper.queryAll(currentFilter);
        adapter.notifyDataSetChanged();
    }

    private class ExpenseAdapter extends BaseAdapter {
        List<ExpenseDbHelper.Expense> data = new ArrayList<>();
        final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());

        @Override
        public int getCount() { return data.size(); }

        @Override
        public Object getItem(int position) { return data.get(position); }

        @Override
        public long getItemId(int position) { return data.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                row.removeAllViews();
            } else {
                row = new LinearLayout(ExpenseActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(16, 20, 16, 20);
                row.setGravity(Gravity.CENTER_VERTICAL);
            }

            ExpenseDbHelper.Expense e = data.get(position);

            // Date
            TextView dateView = new TextView(ExpenseActivity.this);
            dateView.setText(sdf.format(new Date(e.createdAt)));
            dateView.setTextSize(13);
            dateView.setTextColor(Color.GRAY);
            dateView.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 2));

            // Merchant
            TextView merchantView = new TextView(ExpenseActivity.this);
            merchantView.setText(e.merchant != null ? e.merchant : "-");
            merchantView.setTextSize(15);
            merchantView.setTypeface(null, Typeface.BOLD);
            merchantView.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 3));

            // Amount
            TextView amountView = new TextView(ExpenseActivity.this);
            amountView.setText(String.format(Locale.getDefault(), "$%.0f", e.amount));
            amountView.setTextSize(15);
            amountView.setTextColor(Color.parseColor("#D32F2F"));
            amountView.setGravity(Gravity.END);
            amountView.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 2));

            // Category
            TextView catView = new TextView(ExpenseActivity.this);
            catView.setText(e.category != null ? e.category : "");
            catView.setTextSize(12);
            catView.setTextColor(Color.parseColor("#1976D2"));
            catView.setGravity(Gravity.END);
            catView.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 2));

            row.addView(dateView);
            row.addView(merchantView);
            row.addView(amountView);
            row.addView(catView);

            return row;
        }
    }
}
