package br.com.redesurftank.displaypilot;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Display;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Deliberately standalone: the production Impulse process, preferences and permissions are untouched. */
@SuppressLint("SetTextI18n") // This small, PT-BR-only bench pilot has no translation catalog.
public final class PilotActivity extends Activity implements DisplayManager.DisplayListener {
    private static final long MAX_PRESENTATION_MS = 15_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<DisplayTarget> candidates = new ArrayList<>();
    private DisplayManager displayManager;
    private PilotSession session;
    private TextView status, inventory;
    private Spinner spinner;
    private CheckBox parked, clearArea;
    private EditText left, top, width, height;
    private boolean listening;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(12), dp(20), dp(20));
        scroll.addView(column);
        addText(column, "Impulse Display Pilot · 0.1.0", 26);
        addText(column, "Somente bancada ou carro parado. Desenho simulado, sem Waze real. "
                + "Uma janela pode apagar o espelhamento nativo mesmo sendo pequena. "
                + "Se ADAS, alertas ou velocímetro sumirem ou congelarem, toque em PARAR. "
                + "Cada abertura termina automaticamente em 15 segundos.", 18);
        Button stop = new Button(this);
        stop.setText("PARAR PROJEÇÃO AGORA");
        stop.setTextColor(Color.rgb(170, 0, 0));
        root.addView(stop);
        status = addText(root, "Nenhuma projeção aberta.", 20);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        session = new PilotSession(this::openPresentation, message -> status.setText(message));
        stop.setOnClickListener(v -> disarm("Parado pelo operador."));
        inventory = addText(column, "", 16);
        Button refresh = new Button(this);
        refresh.setText("Atualizar lista de displays");
        column.addView(refresh);
        refresh.setOnClickListener(v -> refreshDisplays());
        addText(column, "Destino: escolha pelo ID observado. Não presuma que seja o 3.", 18);
        spinner = new Spinner(this);
        spinner.setSaveEnabled(false);
        column.addView(spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                disarm("Destino selecionado. Confirme a área livre antes de abrir.");
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { disarm("Sem destino."); }
        });
        addText(column, "Janela em % da área acessível: esquerda, topo, largura, altura. "
                + "Os valores iniciais são apenas exemplo, não uma área segura validada.", 18);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(fields);
        left = field(fields, "Esquerda %", "40");
        top = field(fields, "Topo %", "25");
        width = field(fields, "Largura %", "20");
        height = field(fields, "Altura %", "40");
        parked = new CheckBox(this);
        parked.setText("Confirmo: veículo parado / bancada e controle PARAR acessível");
        parked.setSaveEnabled(false);
        column.addView(parked);
        clearArea = new CheckBox(this);
        clearArea.setText("Conferi a posição: essa área não cobre ADAS, velocímetro nem alertas");
        clearArea.setSaveEnabled(false);
        column.addView(clearArea);
        parked.setOnCheckedChangeListener((button, checked) -> { if (!checked) session.stop("Confirmação removida."); });
        clearArea.setOnCheckedChangeListener((button, checked) -> { if (!checked) session.stop("Área não confirmada."); });
        Button preview = new Button(this);
        preview.setText("Ver desenho simulado aqui");
        column.addView(preview);
        MockNavigationView localPreview = new MockNavigationView(this);
        localPreview.setVisibility(View.GONE);
        column.addView(localPreview, new LinearLayout.LayoutParams(-1, dp(200)));
        preview.setOnClickListener(v -> localPreview.setVisibility(
                localPreview.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        Button open = new Button(this);
        open.setText("Abrir SIMULAÇÃO por até 15 segundos");
        column.addView(open);
        open.setOnClickListener(v -> openSelected());
        addText(column, "O desenho usa dados fixos inventados: 300 m à direita, 6,2 km, 18:30. "
                + "Não há mapa, rota, GPS, captura de tela, conexão com o carro ou aplicativo de terceiros. "
                + "Sair desta tela, girar ou desconectar o display encerra a projeção.", 16);
    }

    private void openSelected() {
        int index = spinner.getSelectedItemPosition() - 1;
        DisplayTarget target = index >= 0 && index < candidates.size() ? candidates.get(index) : null;
        try {
            Viewport viewport = new Viewport(number(left), number(top), number(width), number(height));
            session.open(target, viewport, parked.isChecked(), clearArea.isChecked());
        } catch (IllegalArgumentException invalid) {
            session.stop("Área inválida: esquerda/topo 0–90, largura 10–40, altura 10–60; deve caber na tela.");
        }
    }

    private PilotSession.Output openPresentation(DisplayTarget target, Viewport viewport,
            Runnable dismissed) throws Exception {
        if (displayManager == null) throw new IllegalStateException("DisplayManager indisponível.");
        Display display = displayManager.getDisplay(target.id);
        DisplayTarget now = snapshot(display);
        if (!target.sameConfiguration(now) || now.rejection() != null) {
            throw new IllegalStateException("Display mudou; atualize e selecione novamente.");
        }
        PilotPresentation presentation = new PilotPresentation(this, display,
                viewport.pixels(now.width, now.height));
        Runnable timeout = presentation::dismiss;
        presentation.setOnDismissListener(dialog -> {
            handler.removeCallbacks(timeout);
            dismissed.run();
        });
        try {
            presentation.show();
            if (!presentation.isShowing()) throw new IllegalStateException("Janela não exibida.");
            handler.postDelayed(timeout, MAX_PRESENTATION_MS);
            return () -> {
                handler.removeCallbacks(timeout);
                presentation.dismiss();
            };
        } catch (RuntimeException failure) {
            handler.removeCallbacks(timeout);
            // Invalidate the failed instance; never try another ID or request more permission.
            presentation.setOnDismissListener(null);
            try { presentation.dismiss(); } catch (RuntimeException ignored) { }
            finally { presentation.cleanupFailedShow(); }
            throw failure;
        }
    }

    private DisplayTarget snapshot(Display display) {
        if (display == null) return null;
        Point size = new Point();
        display.getSize(size);
        return new DisplayTarget(display.getDisplayId(), display.getName(), size.x, size.y,
                display.getRotation(), display.getFlags(), display.isValid(),
                display.getState() == Display.STATE_ON,
                (display.getFlags() & Display.FLAG_PRIVATE) != 0);
    }

    private void refreshDisplays() {
        disarm("Lista atualizada. Escolha e confirme o destino novamente.");
        candidates.clear();
        List<String> labels = new ArrayList<>();
        labels.add("Selecione um display secundário");
        StringBuilder report = new StringBuilder("Displays visíveis por API pública:\n");
        try {
            Display[] displays = displayManager == null ? new Display[0] : displayManager.getDisplays();
            for (Display display : displays) {
                DisplayTarget target = snapshot(display);
                if (target == null) continue;
                report.append(target).append(" · rotação ").append(target.rotation * 90)
                        .append("° · flags 0x").append(Integer.toHexString(target.flags)).append('\n');
                if (target.rejection() == null) {
                    candidates.add(target);
                    labels.add(target.toString());
                } else report.append("  ").append(target.rejection()).append('\n');
            }
        } catch (RuntimeException failure) {
            candidates.clear();
            labels.clear();
            labels.add("Nenhum destino disponível");
            report.append("Enumeração recusada: ").append(failure.getClass().getSimpleName());
        }
        if (candidates.isEmpty()) report.append("Nenhum display público secundário ligado elegível. Não será tentado outro caminho.");
        inventory.setText(report.toString());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
    }

    private void disarm(String reason) {
        if (session != null) session.stop(reason);
        if (parked != null) parked.setChecked(false);
        if (clearArea != null) clearArea.setChecked(false);
    }

    @Override protected void onResume() {
        super.onResume();
        session.resume();
        if (displayManager != null && !listening) {
            displayManager.registerDisplayListener(this, handler);
            listening = true;
        }
        refreshDisplays();
    }

    @Override protected void onPause() {
        session.pause();
        disarm("Pausado. A projeção foi encerrada.");
        if (displayManager != null && listening) {
            displayManager.unregisterDisplayListener(this);
            listening = false;
        }
        super.onPause();
    }

    @Override protected void onStop() { session.pause(); super.onStop(); }
    @Override protected void onDestroy() {
        session.pause();
        handler.removeCallbacksAndMessages(null);
        if (displayManager != null && listening) displayManager.unregisterDisplayListener(this);
        listening = false;
        super.onDestroy();
    }

    @Override public void onDisplayAdded(int id) { refreshDisplays(); }
    @Override public void onDisplayRemoved(int id) { session.displayChanged(id); refreshDisplays(); }
    @Override public void onDisplayChanged(int id) { session.displayChanged(id); refreshDisplays(); }

    private TextView addText(LinearLayout parent, String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setPadding(0, dp(5), 0, dp(5));
        parent.addView(view);
        return view;
    }

    private EditText field(LinearLayout parent, String label, String value) {
        EditText view = new EditText(this);
        view.setHint(label);
        view.setContentDescription(label);
        view.setText(value);
        view.setSelectAllOnFocus(true);
        view.setInputType(InputType.TYPE_CLASS_NUMBER);
        view.setSaveEnabled(false);
        parent.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
        view.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                disarm("Área alterada. Confira e confirme novamente.");
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        return view;
    }
    private int number(EditText field) { return Integer.parseInt(field.getText().toString().trim()); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
