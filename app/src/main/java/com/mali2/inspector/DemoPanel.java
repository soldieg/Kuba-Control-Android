package com.mali2.inspector;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

final class DemoPanel {
    private DemoPanel() { }

    static void show(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int margin = Math.round(24 * context.getResources().getDisplayMetrics().density);
        panel.setPadding(margin, margin / 2, margin, margin / 2);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);

        label(panel, "Tudo nesta tela é simulado. Os controles atuam somente nesta demonstração.");
        label(panel, "Bateria virtual · Esquerdo 84% · Direito 76% · Estojo 60%");
        select(panel, "Modo acústico", "Normal", "Transparência", "ANC");
        select(panel, "Dois toques no lado esquerdo", "Reproduzir / pausar", "Próxima faixa", "Alternar ANC");
        select(panel, "Dois toques no lado direito", "Reproduzir / pausar", "Próxima faixa", "Alternar ANC");

        int[] bands = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
        for (int frequency : bands) {
            TextView gain = label(panel, frequency + " Hz · 0 dB");
            SeekBar slider = new SeekBar(context);
            slider.setMax(24);
            slider.setProgress(12);
            slider.setContentDescription(frequency + " hertz");
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar view, int progress, boolean fromUser) {
                    gain.setText(frequency + " Hz · " + (progress - 12) + " dB");
                }
                @Override public void onStartTrackingTouch(SeekBar view) { }
                @Override public void onStopTrackingTouch(SeekBar view) { }
            });
            panel.addView(slider);
        }
        new AlertDialog.Builder(context)
            .setTitle("Demonstração de controles")
            .setView(scroll)
            .setPositiveButton("Fechar", null)
            .show();
    }

    private static TextView label(LinearLayout panel, String text) {
        TextView view = new TextView(panel.getContext());
        view.setText(text);
        panel.addView(view);
        return view;
    }

    private static void select(LinearLayout panel, String title, String... choices) {
        label(panel, title);
        Spinner input = new Spinner(panel.getContext());
        input.setContentDescription(title);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            panel.getContext(), android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        input.setAdapter(adapter);
        panel.addView(input);
    }
}
