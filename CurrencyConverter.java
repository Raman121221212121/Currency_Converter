import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.HashMap;
import java.net.*;
import java.io.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

public class CurrencyConverter extends JFrame {

    private static final long serialVersionUID = 1L;

    private JComboBox<String> fromCombo;
    private JComboBox<String> toCombo;
    private JTextField amountField;
    private JButton convertButton;
    private JLabel resultLabel;
    private JCheckBox liveRatesCheck;

    private static final Map<String, BigDecimal> fallbackRates = new HashMap<>();

    static {
        fallbackRates.put("USD", new BigDecimal("1.0000"));
        fallbackRates.put("INR", new BigDecimal("83.5000"));
        fallbackRates.put("EUR", new BigDecimal("0.9200"));
        fallbackRates.put("GBP", new BigDecimal("0.7900"));
        fallbackRates.put("JPY", new BigDecimal("157.2000"));
        fallbackRates.put("AUD", new BigDecimal("1.57"));
        fallbackRates.put("CAD", new BigDecimal("1.36"));
        fallbackRates.put("SGD", new BigDecimal("1.36"));
        fallbackRates.put("CNY", new BigDecimal("7.20"));
        fallbackRates.put("KRW", new BigDecimal("1410.00"));
    }

    public CurrencyConverter() {
        super("Currency Converter");

        String[] currencies = fallbackRates.keySet().toArray(new String[0]);

        fromCombo = new JComboBox<>(currencies);
        toCombo = new JComboBox<>(currencies);
        amountField = new JTextField("1.00", 12);
        convertButton = new JButton("Convert");
        resultLabel = new JLabel("Result: ");
        liveRatesCheck = new JCheckBox("Use live rates (requires internet)");

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Amount:"), gbc);

        gbc.gridx = 1;
        panel.add(amountField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("From:"), gbc);

        gbc.gridx = 1;
        panel.add(fromCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("To:"), gbc);

        gbc.gridx = 1;
        panel.add(toCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(liveRatesCheck, gbc);

        gbc.gridy = 4;
        panel.add(convertButton, gbc);

        gbc.gridy = 5;
        panel.add(resultLabel, gbc);

        add(panel, BorderLayout.CENTER);

        convertButton.addActionListener(e -> onConvert());
        amountField.addActionListener(e -> onConvert());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 340);
        setLocationRelativeTo(null);
    }

    private void onConvert() {
        String from = (String) fromCombo.getSelectedItem();
        String to = (String) toCombo.getSelectedItem();
        String amtText = amountField.getText().trim();

        if (from == null || to == null) {
            JOptionPane.showMessageDialog(this, "Choose both currencies.", "Input error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amtText);
            if (amount.compareTo(BigDecimal.ZERO) < 0)
                throw new NumberFormatException("Negative");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid non-negative number for amount.", "Input error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean useLive = liveRatesCheck.isSelected();
        try {
            Map<String, BigDecimal> rates;

            if (useLive) {
                rates = fetchLiveRates("USD");
                if (rates == null || !rates.containsKey(from) || !rates.containsKey(to)) {
                    rates = fallbackRates;
                    JOptionPane.showMessageDialog(this, "Live rates not available. Using fallback rates.", "Notice", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                rates = fallbackRates;
            }

            BigDecimal result = convert(amount, from, to, rates)
                    .setScale(4, RoundingMode.HALF_UP);

            resultLabel.setText("Result: " + result.toPlainString() + " " + to);

        } catch (Exception ex) {
            ex.printStackTrace();

            JOptionPane.showMessageDialog(this, "Error during conversion: " + ex.getMessage() + "\nUsing fallback rates.", "Error", JOptionPane.ERROR_MESSAGE);

            BigDecimal result = convert(amount, from, to, fallbackRates)
                    .setScale(4, RoundingMode.HALF_UP);

            resultLabel.setText("Result: " + result.toPlainString() + " " + to);
        }
    }

    private BigDecimal convert(BigDecimal amount, String from, String to, Map<String, BigDecimal> rates) {
        if (from.equals(to))
            return amount;

        BigDecimal rateFrom = rates.get(from);
        BigDecimal rateTo = rates.get(to);

        if (rateFrom == null || rateTo == null)
            throw new IllegalArgumentException("Rates missing for currencies.");

        BigDecimal amountInUsd = amount.divide(rateFrom, 12, RoundingMode.HALF_UP);
        return amountInUsd.multiply(rateTo);
    }

    private Map<String, BigDecimal> fetchLiveRates(String base) throws IOException {
        String urlStr = "https://api.exchangerate.host/latest?base=" + URLEncoder.encode(base, "UTF-8");

        URL url = new URL(urlStr);
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

        con.setRequestMethod("GET");
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);

        int status = con.getResponseCode();
        InputStream in = (status >= 200 && status < 300) ? con.getInputStream() : con.getErrorStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }

        reader.close();
        con.disconnect();

        String json = sb.toString();
        Map<String, BigDecimal> rates = new HashMap<>();

        try {
            JSONObject root = new JSONObject(json);
            JSONObject ratesObj = root.getJSONObject("rates");

            for (String key : ratesObj.keySet()) {
                double val = ratesObj.getDouble(key);
                rates.put(key, new BigDecimal(Double.toString(val)));
            }

            return rates;

        } catch (JSONException je) {
            throw new IOException("Failed to parse rates JSON: " + je.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CurrencyConverter app = new CurrencyConverter();
            app.setVisible(true);
        });
    }
}
