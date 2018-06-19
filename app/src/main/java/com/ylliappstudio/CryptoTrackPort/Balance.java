package com.ylliappstudio.CryptoTrackPort;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

class BalanceAdapter extends BaseAdapter {
    private LayoutInflater inflater;

    public BalanceAdapter(Context context) {
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return Balance.trackedIds.size();
    }

    @Override
    public Object getItem(int index) {
        return Balance.trackedIds.get(index);
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(final int index, View convertView, ViewGroup parent) {
        // get the id for this index
        //marrim id per per indeksin
        String id = (String) getItem(index);

        // get the view associated with this listview item
        //merr view qe eshte e ne list item balance
        View view = inflater.inflate(R.layout.list_item_balance, parent, false);

        // set the symbol text
        //vendose simbolin e monedhes perkatse duke u bazuar ne id te saje
        ((TextView) view.findViewById(R.id.coinName)).setText(Balance.GetCoinSymbol(id));

        // set the holdings text
        //vendose balancin e monedhes
        ((TextView) view.findViewById(R.id.coinBalance)).setText(String.format("%1$.3f", Balance.GetCoinHoldings(id)));

        // set the icon of the listview item
        //vendose ikonen per monedhen(krypto valuten) caktuar
        File file = new File(Balance.actContext.getApplicationInfo().dataDir + "/" + id + ".png");
        Uri uri = Uri.fromFile(file);
        ((ImageView) view.findViewById(R.id.coinIcon)).setImageURI(uri);

        // set the price text
        //vendose cmimin e valutes
        ((TextView) view.findViewById(R.id.coinPrice)).setText(Balance.GetCurrencySign() + String.format("%1$,.2f", Balance.GetCoinPrice(id)));

        // set the value of the holdings text
        //vendose cmimin e sasise se monedhes
        ((TextView) view.findViewById(R.id.coinValue)).setText(Balance.GetCurrencySign() + String.format("%1$,.2f", Balance.GetCoinValue(id)));

        // set the percent changed text
        //vendos ngjyren e perqindejes sipas ndryshimit
        double change = Balance.GetCoinPriceChange(id);
        TextView changeText = (TextView) view.findViewById(R.id.percentChange);
        if (change > 0) {
            changeText.setText("+" + String.format("%1$,.2f", change) + "%");
            changeText.setTextColor(Color.rgb(0, 150, 0));
            ((ImageView) view.findViewById(R.id.changeArrow)).setImageResource(R.drawable.arrow_green);
        } else if (change < 0) {
            changeText.setText(String.format("%1$,.2f", change) + "%");
            changeText.setTextColor(Color.RED);
            ((ImageView) view.findViewById(R.id.changeArrow)).setImageResource(R.drawable.arrow_red);
        } else {
            changeText.setText("+" + String.format("%1$,.2f", change) + "%");
            changeText.setTextColor(Color.BLACK);
            ((ImageView) view.findViewById(R.id.changeArrow)).setImageResource(R.drawable.dash);
        }

        return view;
    }
}

class CoinSelectAdapter extends BaseAdapter {
    private LayoutInflater inflater;

    //me kqyre qka osht inflater
    public CoinSelectAdapter(Context context) {
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return Balance.NumCoinsWithSymbol(Balance.SelectedCoin);
    }

    @Override
    public Object getItem(int index) {
        return Balance.GetCoinId(Balance.SelectedCoin, index);
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(final int index, View convertView, ViewGroup parent) {
        // get the id for this index
        String id = (String) getItem(index);

        // get the view associated with this listview item
        View view = inflater.inflate(R.layout.list_item_coin, parent, false);

        // set the name of the coin
        ((TextView) view.findViewById(R.id.coinName)).setText(Balance.GetCoinName(id));

        return view;
    }
}


public class Balance extends Activity {
    //Variablat Statike Region
    public static LinkedList<String> trackedIds = new LinkedList<>();  //Lista e sortuar e Idve qe i bon track
    public static JSONObject data = new JSONObject(); //te gjitha informatat e nevojshme rruhen ketu ne vend te databazes qe me punu me lehte me to

    static BalanceAdapter balanceAdapter = null;
    static CoinSelectAdapter coinSelectAdapter = null;
    static Context actContext;

    static String SelectedCoin = ""; //simboli i valutes
    static boolean paused = false; //variabel qe ndalon rifreskimin kur eshte joaktive
    static boolean updating = false; //variable qe ndalon/lejon update
    static long updateDelay = 60000; //koha ne mes update ne milisekonda
    static boolean selectingCoin = false; //e mbyll boxin per zgjedhjen e coin kur e shtyp butonin prapa

    static boolean addToExistingBalance = false;

    static Map<String, String> currencies = new HashMap<>();
    //endregion

    //Qasja ne te dhenat region
    public static String GetTimeFrame() {
        try {
            if (data.has("timeFrame")) {
                return data.getString("timeFrame");
            }
        } catch (Exception e) {}
        return "percent_change_24h"; //Perdore ndryshimin 24h per default
    }

    public static void NextTimeFrame() {
        try {
            String value = GetTimeFrame();
            if (value.equals("percent_change_24h")) {
                data.put("timeFrame", "percent_change_7d");
            } else if (value.equals("percent_change_7d")) {
                data.put("timeFrame", "percent_change_1h");
            } else {
                data.put("timeFrame", "percent_change_24h");
            }
        } catch (Exception e) {}
    }

    public static String GetSortBy() {
        try {
            if (data.has("sortBy")) {
                return data.getString("sortBy");
            }
        } catch (Exception e) {}
        return "coin"; //sortoj duke u bazuar ne monedhe default
    }

    public static void SetSortBy(String value) {
        try {
            data.put("sortBy", value);
        } catch (Exception e) {}
    }

    public static String GetCurrency() {
        try {
            if (data.has("currencyCode")) {
                return data.getString("currencyCode");
            }
        } catch (Exception e) {}
        return "EUR"; // perdore EUR formatin default
    }

    public static void SetCurrency(String value) {
        try {
            data.put("currencyCode", value);
        } catch (Exception e) {}
    }

    public static String GetCurrencySign() {
        if (currencies.containsKey(GetCurrency())) {
            return currencies.get(GetCurrency());
        }
        return "";
    }

    public static boolean GetSortDesc() {
        try {
            if (data.has("sortDesc")) {
                return data.getBoolean("sortDesc");
            }
        } catch (Exception e) {}
        return true; // sortoj nga ma e larta default
    }

    public static void SetSortDesc(boolean value) {
        try {
            data.put("sortDesc", value);
        } catch (Exception e) {}
    }

    public static Date GetLastUpdate() {
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return df.parse(data.getString("lastUpdate"));
        } catch (Exception e) {}
        return new Date(0);
    }

    public static void SetLastUpdate() {
        try {
            Date now = Calendar.getInstance().getTime();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            data.put("lastUpdate", df.format(now));
        } catch (Exception e) {}
    }

    public static LinkedList<String> GetTrackedIds() {
        LinkedList<String> result = new LinkedList<>();
        try {
            for (int i = 0; i < data.getJSONObject("trackedIds").length(); i++){
                result.add(data.getJSONObject("trackedIds").names().getString(i));
            }
        } catch (Exception e) {}
        return result;
    }

    public static void RemoveTrackedId(String id) {
        try {
            data.getJSONObject("trackedIds").remove(id);
        } catch (Exception e) {}
    }

    public static int NumCoinsWithSymbol(String symbol) {
        try {
            return data.getJSONObject("ids").getJSONArray(symbol).length();
        } catch (Exception e) {}
        return 0;
    }

    public static String GetCoinId(String symbol, int index) {
        try {
            return data.getJSONObject("ids").getJSONArray(symbol).getString(index);
        } catch (Exception e) {}
        return "ERROR";
    }

    public static String GetCoinSymbol(String id) {
        try {
            return data.getJSONObject("coins").getJSONObject(id).getString("symbol");
        } catch (Exception e) {}
        return "ERROR";
    }

    public static String GetCoinName(String id) {
        try {
            return data.getJSONObject("coins").getJSONObject(id).getString("name");
        } catch (Exception e) {}
        return "ERROR";
    }

    public static String GetCoinWebsiteSlug(String id) {
        try {
            return data.getJSONObject("coins").getJSONObject(id).getString("website_slug");
        } catch (Exception e) {}
        return "ERROR";
    }

    public static Double GetCoinPrice(String id) {
        try {
            return data.getJSONObject("coins").getJSONObject(id).getJSONObject("quotes").getJSONObject(GetCurrency()).getDouble("price");
        } catch (Exception e) {}
        return 0d;
    }

    public static Double GetCoinPriceChange(String id) {
        try {
            return data.getJSONObject("coins").getJSONObject(id).getJSONObject("quotes").getJSONObject(GetCurrency()).getDouble(GetTimeFrame());
        } catch (Exception e) {}
        return 0d;
    }

    public static Double GetCoinHoldings(String id) {
        try {
            return data.getJSONObject("trackedIds").getJSONObject(id).getDouble("holdings");
        } catch (Exception e) {}
        return 0d;
    }

    public static void SetCoinHoldings(String id, double holdings) {
        try {
            //vendoes kete id nese nuk gjinden ne liste
            if (!data.has("trackedIds")) {
                data.put("trackedIds", new JSONObject());
            }

            //vendose per ndjekje(track) kete id nese nuk eshte
            if (!data.getJSONObject("trackedIds").has(id)) {
                data.getJSONObject("trackedIds").put(id, new JSONObject());
            }

            //vendos sasine e zoteruar per kete id
            data.getJSONObject("trackedIds").getJSONObject(id).put("holdings", holdings);
        } catch (Exception e) {}
    }

    public static Double GetCoinValue(String id) {
        return GetCoinPrice(id) * GetCoinHoldings(id);
    }
    //endregion

    //SAVE/LOAD region
     void Load() {
        try {
            //merri te dhenat nga fajlli json qe e kemi krijuar per te ruajtur te dhenat ne vend te db
            InputStream inputStream = openFileInput("data.json");
            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                data = new JSONObject(bufferedReader.readLine());
                inputStream.close();
            }
        }
        catch (Exception e) {
            Log.e("ERROR", e.getMessage(), e);
        }
    }

    void Save() {
        try {
            //ruaj te dhenat ne fajllin data.json
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput("data.json", Context.MODE_PRIVATE));
            outputStreamWriter.write(data.toString());
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Rruajta e bilancit", e.toString());
        }
    }
    //endregion

    //Buttonat qe gjinden pran titullit region
    //E hap profilin tim ne github
    public void OpenReadme(View v) {
        Intent intent= new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/YlliBala/"));
        startActivity(intent);
    }

    public void OpenCurrencyBox(View v) {
        //e hap texview edhe e vendos tekstin ""
        ((TextView) findViewById(R.id.currencyInput)).setText("");

        // e ban disable toggleinput
        ToggleInput(false);

        //e shfaq editbox
        findViewById(R.id.setCurrencyBox).setVisibility(FrameLayout.VISIBLE);

        //e shfaq tastieren
        EditText editText = (EditText) findViewById(R.id.currencyInput);
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
    }

    public void ChangeCurrency() {
        //e nxjer ne format stringu inicialet e monedhes momentale
        String currency = ((TextView) findViewById(R.id.currencyInput)).getText().toString();

        //e mbyll currencyBox
        CloseCurrencyBox();

        //E vendos monedhen e re(formatin psh USD to EUR)
        SetCurrency(currency);

        //Update...
        Update();
    }

    public void CloseCurrencyBox() {
        //Mbyll tastieren
        EditText editText = (EditText) findViewById(R.id.currencyInput);
        editText.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

        //Gjithashtu mbyllet edhe edit box
        findViewById(R.id.setCurrencyBox).setVisibility(FrameLayout.GONE);

        //ToggleInput bohet enable
        ToggleInput(true);
    }

    public void Refresh(View v) {
        Update();
    }

    public void ChangeTimeFrame(View v) {
        //e ndyshon formatin e matjes se kohes se perqindjes
        NextTimeFrame();

        // update
        UpdateDisplay();
    }
    //endregion

    //Sortimet region
    void Sort() {
        //Lista me Id te monedhave
        trackedIds = GetTrackedIds();

        //Krijon nje krahasues per sortim
        Comparator comparator = new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                int result = GetCoinSymbol(rhs).compareTo(GetCoinSymbol(lhs));
                if (GetSortDesc()) {
                    return result * -1;
                }
                return result;
            }
        };
        if (GetSortBy().equals("holdings")) {
            comparator = new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    int result = Double.compare(GetCoinValue(lhs), GetCoinValue(rhs));
                    if (GetSortDesc()) {
                        result *= -1;
                    }
                    return result;
                }
            };
        } else if (GetSortBy().equals("price")) {
            comparator = new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    int result = Double.compare(GetCoinPriceChange(lhs), GetCoinPriceChange(rhs));
                    if (GetSortDesc()) {
                        result *= -1;
                    }
                    return result;
                }
            };
        }

        //Bene sortimin sipas krahasuesit qe u deklaruar me larte
        Collections.sort(trackedIds, comparator);
    }

    public void SortByValue(String value) {
        //Nese ka qene e sortume per nga cmimi
        if (GetSortBy().equals(value)) {
            // ndrysho drejtimin e sortimit
            SetSortDesc(!GetSortDesc());
        }
        // nese nuk ka qene e sortume nga cmimi
        else {
            //sorto nga lart poshte
            SetSortBy(value);
            SetSortDesc(true);
        }

        // update
        UpdateDisplay();
    }

    public void SortByCoin(View v) {
        SortByValue("coin");
    }

    public void SortByHoldings(View v) {
        SortByValue("holdings");
    }

    public void SortByPrice(View v) {
        SortByValue("price");
    }
    //endregion

    //Vendosa e nje monedhe te re region
    public void OpenCoinInputBox(View v) {
        //vendos tekstin "" kur te hapet texView
        ((TextView) findViewById(R.id.coinInput)).setText("");

        // ToggleIput disable
        ToggleInput(false);

        //Shfaqe edit box-in
        findViewById(R.id.newCoinBox).setVisibility(FrameLayout.VISIBLE);

        //Shfaq tastieren
        EditText editText = (EditText) findViewById(R.id.coinInput);
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
    }

    public void AddCoin() {
        //Merr inicialet e monedhes nga texView
        String symbol = ((TextView) findViewById(R.id.coinInput)).getText().toString();

        // Mbylle new coin box
        CloseNewCoinBox();

        //Nese egziston vetem nje id qe i pershtatet inicialve te monedhes athere vazhdo me shtimin e monedhes
        if (NumCoinsWithSymbol(symbol) == 1) {
            //merr id nga inicialet e monedhes
            SelectedCoin = GetCoinId(symbol, 0);

            //Download ikonen
            DownloadIcon(SelectedCoin);

            //Shton mbi balancen egzistuse
            addToExistingBalance = true;

            //shfaq pjesen per ta shenuar bilancin
            OpenBalanceInputBox();
        }
        //Nese ka me shume id me inicialte te njejta
        else if (NumCoinsWithSymbol(symbol) > 1) {
            //zgjidh monedhen me simbolin specifik
            SelectedCoin = symbol;

            //Shfaqe pjesen per zgjdhjen se cilin monedh e ka kerkuar
            OpenCoinSelectorBox();
        }
    }

    public void OpenCoinSelectorBox() {
        //mundeson qe me an te prekjes se butonit prapa te largohet pamja
        selectingCoin = true;

        //bene update coinSelectAdapter
        coinSelectAdapter.notifyDataSetChanged();

        // ToggleInput disable
        ToggleInput(false);

        //i shfaq monedheat qe jan shfaqur me titull te njejte
        ((TextView) findViewById(R.id.chooseCoinTitle)).setText("Cila " + SelectedCoin + "?");

        //E shfaq coinSelector per me zgjedh se cilen monedh e zgjedhim
        findViewById(R.id.coinSelector).setVisibility(FrameLayout.VISIBLE);
    }

    public void ChooseSelected() {
        //E mbyll Coin Selector Box
        CloseCoinSelectorBox();

        //Download icon
        DownloadIcon(SelectedCoin);

        //Shton mbi balancen egzistuse
        addToExistingBalance = true;

        // shfaq pjesen per ta shenuar bilancin
        OpenBalanceInputBox();
    }

    public void CloseCoinSelectorBox() {
        // mundeson qe me prekjen e buttonit prapa te mbyllet aplikacioni
        selectingCoin = false;

        //ToggleInput enable
        ToggleInput(true);

        //Mbylle coinselector
        findViewById(R.id.coinSelector).setVisibility(FrameLayout.GONE);
    }

    public void CloseNewCoinBox() {
        //Mbylle tastieren
        EditText editText = (EditText) findViewById(R.id.coinInput);
        editText.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

        //Mbylle edit box
        findViewById(R.id.newCoinBox).setVisibility(FrameLayout.GONE);

        //ToggleInput enable
        ToggleInput(true);
    }
    //endregion

    //Vendosja e Bilancit region
    public void OpenBalanceInputBox() {
        //Vendos titullin te pjesa e vendoses se bilanit per monedhen e caktuar
        if (addToExistingBalance) {                             //Shto në sasin e
            ((TextView) findViewById(R.id.editTitle)).setText("Shto në sasin e " + GetCoinName(SelectedCoin));
            ((TextView) findViewById(R.id.balanceInput)).setText("");
        } else {                                    //Vendos sasinë e Zotëruar të *coinit
            ((TextView) findViewById(R.id.editTitle)).setText("Vendos sasinë e Zotëruar të " + GetCoinName(SelectedCoin) );
            ((TextView) findViewById(R.id.balanceInput)).setText(Double.toString(GetCoinHoldings(SelectedCoin)));
        }

        // ToggleInput disable
        ToggleInput(false);

        //Shfaq editBox
        findViewById(R.id.editBalanceBox).setVisibility(FrameLayout.VISIBLE);

        //Shfaq bilancin
        EditText editText = (EditText) findViewById(R.id.balanceInput);
        editText.requestFocus();
        editText.selectAll();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
    }

    public void DeleteSelected(View v) {
        //Fshij ikonen e monedhes se caktuar
        File file = new File(Balance.actContext.getApplicationInfo().dataDir + "/" + SelectedCoin + ".png");
        if (file.exists()) {
            file.delete();
        }

        //Largo monedhen nga lista
        RemoveTrackedId(SelectedCoin);

        //Mbylle edit box
        CloseBalanceEditBox();

        // update
        UpdateDisplay();
    }

    public void SaveBalance() {
        //merr sasin e te zotuarave(holdings) prej inputBox
        String balance = ((TextView) findViewById(R.id.balanceInput)).getText().toString();

        // mbyll balance box
        CloseBalanceEditBox();

        //Shto sasin e vendosur mbi ato te meparshmet
        double prevHoldings = GetCoinHoldings(SelectedCoin);
        double newHoldings = (balance.isEmpty()) ? 0 : Double.parseDouble(balance);

        //Vendos holdings per monedhen e caktuar
        if (addToExistingBalance) {
            SetCoinHoldings(SelectedCoin, prevHoldings + newHoldings);
        } else {
            SetCoinHoldings(SelectedCoin, newHoldings);
        }

        // update
        UpdateDisplay();
    }

    public void CloseBalanceEditBox() {
        // Mbyll tastieren
        EditText editText = (EditText) findViewById(R.id.balanceInput);
        editText.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

        // mbyll edit box
        findViewById(R.id.editBalanceBox).setVisibility(FrameLayout.GONE);

        // ToggleInput enable
        ToggleInput(true);
    }
    //endregion

    public void LookupSelected() {
        String webSlug = GetCoinWebsiteSlug(SelectedCoin);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://coinmarketcap.com/currencies/" + webSlug));
        startActivity(intent);
    }

    public void ToggleInput(boolean toggle) {
        // kercej buttonat on/off
        findViewById(R.id.balancesList).setEnabled(toggle);
        findViewById(R.id.refreshButton).setEnabled(toggle);
        findViewById(R.id.helpButton).setEnabled(toggle);
        findViewById(R.id.sortCoinButton).setEnabled(toggle);
        findViewById(R.id.sortHoldingsButton).setEnabled(toggle);
        findViewById(R.id.sortPriceButton).setEnabled(toggle);

        // kerce greyOverlay on/off
        if (toggle) {
            findViewById(R.id.greyOverlay).setVisibility(LinearLayout.GONE);
        } else {
            findViewById(R.id.greyOverlay).setVisibility(LinearLayout.VISIBLE);
        }
    }
                             //Funksioni per download icon nga CoinMarketCap.com appi
    void DownloadIcon(final String id) {
        Log.d("Download ikonen me ", "id = " + id);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    // download ikonen nese nuk gjindet
                    File file = new File(Balance.actContext.getApplicationInfo().dataDir + "/" + id + ".png");
                    if (!file.exists()) {
                        URL iconUrl = new URL("https://s2.coinmarketcap.com/static/img/coins/128x128/" + id + ".png");
                        InputStream in = new BufferedInputStream(iconUrl.openStream());
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int n = 0;
                        while (-1 != (n = in.read(buf))) {
                            out.write(buf, 0, n);
                        }
                        out.close();
                        in.close();
                        byte[] response = out.toByteArray();

                        FileOutputStream fos = new FileOutputStream(Balance.actContext.getApplicationInfo().dataDir + "/" + id + ".png");
                        fos.write(response);
                        fos.close();
                    }
                } catch (Exception e) {
                    Log.e("ErrorDownloadIkona", e.getMessage(), e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void empty) {
                super.onPostExecute(empty);
                UpdateDisplay();
            }
        }.execute();
    }

    // Update region
    Handler handler = new Handler();
    void AutoUpdate() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!paused) {
                    Update();
                }
                AutoUpdate();
            }
        }, updateDelay);
    }

    boolean ShouldUpdate() {
        Date last_update = GetLastUpdate();
        Date next_update = new Date(last_update.getTime() + updateDelay);
        Date now = Calendar.getInstance().getTime();
        if (now.after(next_update)) {
            return true;
        }
        return false;
    }

    @SuppressLint("StaticFieldLeak")
    void Update() {
        if (!updating) {
            // Ndalon qe te behet Update gjat berjes se Update
            updating = true;

            // Animacioni per buttonin e perditsimit
            RotateAnimation anim = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            anim.setInterpolator(new LinearInterpolator());
            anim.setRepeatCount(Animation.INFINITE);
            anim.setDuration(1000);
            final ImageView refreshButton = (ImageView) findViewById(R.id.refreshButton);
            refreshButton.startAnimation(anim);

            //Ruaje kohen qe eshte bere update ne menyre qe te dihet kur mund ta bjeme update te re
            SetLastUpdate();

            //Duke e bere update me nje process tjeter
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        //Download te gjitha informacionet per monedhen e caktuar nga CoinMarketCap.com api
                        int start = 1;
                        JSONObject ids = new JSONObject(); //Per secilin simbol te mondedhe nga nje ID duke filluar nje 1
                        JSONObject coinsData = new JSONObject(); //Mbledh te gjitha informacionit per monedhen specifike

                        while (true) {
                            try {
                                URL url = new URL("https://api.coinmarketcap.com/v2/ticker/?convert=" + GetCurrency() + "&limit=100&start=" + start);
                                InputStream inputStream = url.openStream();
                                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
                                StringBuilder jsonBuilder = new StringBuilder("");
                                String line;
                                while ((line = in.readLine()) != null) {
                                    jsonBuilder.append(line);
                                }
                                JSONObject result = new JSONObject(jsonBuilder.toString());
                                in.close();

                                //perfundon mbledhejen kur nuk ka me te dhena
                                if (result.isNull("data")) {
                                    break;
                                }


                                //Vendosi informacionet e monedhes ne 'data'
                                JSONObject coinsInfo = result.getJSONObject("data");
                                Iterator<String> keys = coinsInfo.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next();


                                    //vendosi ne mapping monedhat duhet e perdoruri si key Id (key => coinInfo)
                                    JSONObject coinInfo = coinsInfo.getJSONObject(key);
                                    coinsData.put(key, coinInfo);


                                    //Nxjer inicialet e monedhes
                                    String coinSymbol = coinInfo.getString("symbol");


                                    //Krijon nje liste me inicialte te monedhave dhe e vendos monedhen ne ate liste nese nuk gjinet paraprakisht
                                    if (!ids.has(coinSymbol)) {
                                        ids.put(coinSymbol, new JSONArray());
                                    }


                                    //Vendos id te kesaj monedhe ne mapping te ID-ve
                                    ids.getJSONArray(coinSymbol).put(key);
                                }

                                // increment start
                                start += 100;
                            } catch (Exception e) { break; }
                        }

                        data.put("ids", ids);
                        data.put("coins", coinsData);
                    } catch (Exception e) {
                        Log.e("Perditsimi i cimimit", e.toString());
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void empty) {
                    super.onPostExecute(empty);
                    UpdateDisplay();
                }
            }.execute();
        }
    }

    void UpdateDisplay() {
        // Rruaj te dhenat
        Save();


        //Sortoj monedhat
        Sort();

        // Ndryshoj Shigjetat e monedhave
        TextView coinHeader = (TextView) findViewById(R.id.sortCoinButton);
        TextView holdingsHeader = (TextView) findViewById(R.id.sortHoldingsButton);
        TextView priceHeader = (TextView) findViewById(R.id.sortPriceButton);
        coinHeader.setText("Monedha");
        holdingsHeader.setText("Të zotruara");
        priceHeader.setText("Çmimi");
        String arrow = (GetSortDesc()) ? "↓" : "↑";
        if (GetSortBy().equals("coin")) {
            coinHeader.setText("Monedha" + arrow);
        } else if (GetSortBy().equals("holdings")) {
            holdingsHeader.setText("Të Zotruara" + arrow);
        } else if (GetSortBy().equals("price")) {
            priceHeader.setText("Çmimi" + arrow);
        }


        //Update vleren totale edhe vleren e kaluar, e cila do te perdoret ne shfaqjen e % perqindjes
        double totalValue = 0;
        double previousValue = 0;
        for (int i = 0; i < trackedIds.size(); i++) {
            String id = trackedIds.get(i);
            totalValue += GetCoinValue(id);

        }
        ((TextView) findViewById(R.id.valueTotal)).setText(String.format("%1$,.2f", totalValue));

        // Vendos titullin e PortFolios
        String portfolioTitle = "Vlera Totale e Portfolios (" + GetCurrency() + ")";
        ((TextView) findViewById(R.id.portfolioValueTitle)).setText(portfolioTitle);
        ((TextView) findViewById(R.id.smallDollarSign)).setText(GetCurrencySign());


        //Vendos titujt per ndryshimet e monedhave
        String changeTitle = "Ndryshimi 24hr";
        if (GetTimeFrame().equals("percent_change_1h")) {
            changeTitle = "Ndryshimi 1hr";
        } else if (GetTimeFrame().equals("percent_change_7d")) {
            changeTitle = "Ndryshimi 7d";
        }
        ((TextView) findViewById(R.id.changeTitle)).setText(changeTitle);


        //Beje Update vlerat e ndryshuara per shfaqje te perqindjeve
        //Gjithashtu shfaq pershtate ngjyren duke u bazuar ne fitim dhe hubje
        TextView changeText = (TextView) findViewById(R.id.change);
        double percentChange = (previousValue == 0) ? 0 : 100 * (totalValue - previousValue) / previousValue;
        if (percentChange > 0) {
            changeText.setText("+" + String.format("%1$,.2f", percentChange) + "%");
            changeText.setTextColor(Color.rgb(0, 150, 0));
        } else if (percentChange < 0) {
            changeText.setText(String.format("%1$,.2f", percentChange) + "%");
            changeText.setTextColor(Color.RED);
        } else {
            changeText.setText("+" + String.format("%1$,.2f", percentChange) + "%");
            changeText.setTextColor(Color.BLACK);
        }


        //Ndrysho ngjyren e shigjetes duke u bazuar ne fitim dhe humbje
        ImageView portfolioArrow = (ImageView) findViewById(R.id.portfolioChangeArrow);
        if (percentChange < 0) {
            portfolioArrow.setImageResource(R.drawable.arrow_red);
        } else if (percentChange > 0) {
            portfolioArrow.setImageResource(R.drawable.arrow_green);
        } else {
            portfolioArrow.setImageResource(R.drawable.dash);
        }


        //Update BalanceAdapter
        balanceAdapter.notifyDataSetChanged();


        //Ndalon rrotullimin e butonit (animacion)
        final ImageView refreshButton = (ImageView) findViewById(R.id.refreshButton);
        refreshButton.setAnimation(null);

        // Tregon se update ka perfunduar dhe mund te behete perseri update
        updating = false;
    }
    //endregion

    //Regjioni i akticiteteve fillestar
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        actContext = this;
        setContentView(R.layout.activity_balance);


        //Inicializimi i mappingut me vlerave te monedhave
        currencies.put("USD", "$");
        currencies.put("AUD", "$");
        currencies.put("BRL", "R$");
        currencies.put("CAD", "$");
        currencies.put("CHF", "");
        currencies.put("CLP", "$");
        currencies.put("CNY", "¥");
        currencies.put("CZK", "Kč");
        currencies.put("DKK", "kr");
        currencies.put("EUR", "€");
        currencies.put("GBP", "£");
        currencies.put("HKD", "$");
        currencies.put("HUF", "Ft");
        currencies.put("IDR", "Rp");
        currencies.put("ILS", "₪");
        currencies.put("INR", "₹");
        currencies.put("JPY", "¥");
        currencies.put("KRW", "₩");
        currencies.put("MXN", "$");
        currencies.put("MYR", "RM");
        currencies.put("NOK", "kr");
        currencies.put("NZD", "$");
        currencies.put("PHP", "₱");
        currencies.put("PKR", "₨");
        currencies.put("PLN", "zł");
        currencies.put("RUB", "\u20BD");
        currencies.put("SEK", "kr");
        currencies.put("SGD", "$");
        currencies.put("THB", "฿");
        currencies.put("TRY", "₺");
        currencies.put("TWD", "$");
        currencies.put("ZAR", "R");


        //Nxjeri te dhenat nga fajlli data
        Load();


        //Vendose listen me bilance
        ListView listView = (ListView) findViewById(R.id.balancesList);
        balanceAdapter = new BalanceAdapter(this);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
                SelectedCoin = (String) balanceAdapter.getItem(position);
                LookupSelected();
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapter, View v, int position, long id) {
                SelectedCoin = (String) balanceAdapter.getItem(position);
                addToExistingBalance = false; // overwrite existing balance
                OpenBalanceInputBox();
                return true;
            }
        });

        listView.setAdapter(balanceAdapter);


        //Vendose listen e monedhave
        final ListView coinListView = (ListView) findViewById(R.id.collidingCoins);
        coinSelectAdapter = new CoinSelectAdapter(this);

        coinListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
                SelectedCoin = (String) coinSelectAdapter.getItem(position);
                ChooseSelected();
            }
        });

        coinListView.setAdapter(coinSelectAdapter);


        //vendose cyrrencyInput
        BalanceEditText currencyInput = (BalanceEditText) findViewById(R.id.currencyInput);


        //Ruaje balancen dhe mbylle inputBox kur breket buttoni per submit ne tastier
        currencyInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    ChangeCurrency();
                    return true;
                }
                return false;
            }
        });

        currencyInput.setKeyImeChangeListener(new BalanceEditText.KeyImeChange() {
            @Override
            public void onKeyIme(int keyCode, KeyEvent event) {
                if (KeyEvent.KEYCODE_BACK == event.getKeyCode()) {
                    CloseCurrencyBox();
                }
            }
        });


        //Vendose editBox
        BalanceEditText balanceInput = (BalanceEditText) findViewById(R.id.balanceInput);


        //Ruaje balancen dhe mbylle inputBox kur breket buttoni per submit ne tastier
        balanceInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    SaveBalance();
                    return true;
                }
                return false;
            }
        });

        balanceInput.setKeyImeChangeListener(new BalanceEditText.KeyImeChange() {
            @Override
            public void onKeyIme(int keyCode, KeyEvent event) {
                if (KeyEvent.KEYCODE_BACK == event.getKeyCode()) {
                    CloseBalanceEditBox();
                }
            }
        });


        //vendose coinBox
        BalanceEditText newCoinInput = (BalanceEditText) findViewById(R.id.coinInput);


        //Ruaje balancen dhe mbylle inputBox kur breket buttoni per submit ne tastier
        newCoinInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    AddCoin();
                    return true;
                }
                return false;
            }
        });

        newCoinInput.setKeyImeChangeListener(new BalanceEditText.KeyImeChange() {
            @Override
            public void onKeyIme(int keyCode, KeyEvent event) {
                if (KeyEvent.KEYCODE_BACK == event.getKeyCode()) {
                    CloseNewCoinBox();
                }
            }
        });

        // Update
        AutoUpdate();
    }

    @Override
    public void onBackPressed() {

        //Vendos funksionin kur te preket buttoni back duke e mbyllur selectorBox ne vend se te mbyllet applikacioni
        if (selectingCoin) {
            CloseCoinSelectorBox();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateDisplay();
        if (ShouldUpdate()) {
            Update();
        }
        paused = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
    }
    //endregion
}
