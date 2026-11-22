package io.github.long0823.tclmirakurun;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView output;
    private TextView networkAddresses;
    private TextView serverState;
    private final Handler networkRefresh=new Handler(Looper.getMainLooper());
    private int refreshTicks;
    private final Runnable refreshNetwork=new Runnable(){public void run(){if(networkAddresses!=null)networkAddresses.setText(shortAddress());if(++refreshTicks%6==0)updateServerState();networkRefresh.postDelayed(this,5000);}};
    private volatile boolean rebootScheduled;
    private volatile boolean serverCheckRunning;
    private int openSection=0;
    private boolean userChoseSection=false;
    private boolean autoSectionDone=false;
    private LinearLayout[] sectionArr;
    private Button[] tileArr;
    private final List<Button> buttons=new ArrayList<>();
    interface Operation { String run() throws Exception; }
    // Material You style palette (dark, teal seed) — same as the 3wave setup app
    private static final int COL_BG=Color.rgb(13,18,22);
    private static final int COL_CARD=Color.rgb(27,35,41);
    private static final int COL_BTN=Color.rgb(38,50,59);
    private static final int COL_BTN_FOCUS=Color.rgb(52,72,84);
    private static final int COL_PRIMARY=Color.rgb(11,110,79);
    private static final int COL_PRIMARY_FOCUS=Color.rgb(16,143,104);
    private static final int COL_DANGER=Color.rgb(96,34,32);
    private static final int COL_DANGER_FOCUS=Color.rgb(133,44,40);
    private static final int COL_TEXT=Color.rgb(227,235,238);
    private static final int COL_MUTED=Color.rgb(192,208,215);
    private static final int COL_ACCENT=Color.rgb(125,218,190);
    private static final int COL_OK=Color.rgb(105,222,155);
    private static final int COL_OFF=Color.rgb(140,155,163);
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable shape(int color,int radiusDp,int strokeColor,int strokeDp) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radiusDp));
        if(strokeDp>0)d.setStroke(dp(strokeDp),strokeColor);
        return d;
    }
    private TextView section(LinearLayout parent,String label) {
        TextView v=new TextView(this); v.setText(label); v.setTextSize(14); v.setTextColor(COL_ACCENT);
        v.setPadding(dp(4),dp(10),0,dp(2)); parent.addView(v); return v;
    }
    private void note(LinearLayout parent,String value) {
        TextView v=new TextView(this); v.setText(value); v.setTextSize(14); v.setTextColor(COL_MUTED);
        v.setPadding(dp(4),dp(2),dp(4),dp(6)); parent.addView(v);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(48),dp(18),dp(48),dp(18)); body.setBackgroundColor(COL_BG);
        scroll.addView(body); setContentView(scroll);
        TextView title=new TextView(this); title.setText("TCL TV Mirakurun 3波対応版"); title.setTextSize(26); title.setTextColor(Color.WHITE); title.setTypeface(null,android.graphics.Typeface.BOLD); body.addView(title);
        TextView description=new TextView(this); description.setText("対応TCL G06・Android 11・Magisk向け"); description.setTextSize(14); description.setTextColor(COL_MUTED); body.addView(description);
        // ---- Status dashboard card ----
        LinearLayout dash=new LinearLayout(this); dash.setOrientation(LinearLayout.VERTICAL);
        dash.setBackground(shape(COL_CARD,20,0,0)); dash.setPadding(dp(20),dp(12),dp(20),dp(12));
        LinearLayout.LayoutParams dashLp=new LinearLayout.LayoutParams(-1,-2); dashLp.setMargins(0,dp(14),0,dp(4)); body.addView(dash,dashLp);
        TextView dashLabel=new TextView(this); dashLabel.setText("いまの状態"); dashLabel.setTextSize(14); dashLabel.setTextColor(COL_ACCENT); dash.addView(dashLabel);
        LinearLayout dashCols=new LinearLayout(this); dashCols.setOrientation(LinearLayout.HORIZONTAL); dash.addView(dashCols);
        LinearLayout dashL=new LinearLayout(this); dashL.setOrientation(LinearLayout.VERTICAL); dashL.setPadding(0,0,dp(10),0); dashCols.addView(dashL,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout dashR=new LinearLayout(this); dashR.setOrientation(LinearLayout.VERTICAL); dashR.setPadding(dp(10),0,0,0); dashCols.addView(dashR,new LinearLayout.LayoutParams(0,-2,1));
        serverState=new TextView(this); serverState.setText("Mirakurun：確認中…"); serverState.setTextSize(19); serverState.setTypeface(null,android.graphics.Typeface.BOLD); serverState.setTextColor(COL_TEXT); serverState.setPadding(0,dp(6),0,0); dashL.addView(serverState);
        TextView addresses=new TextView(this);networkAddresses=addresses;addresses.setText(shortAddress()); addresses.setTextSize(16); addresses.setTextColor(COL_ACCENT); addresses.setTextIsSelectable(true); addresses.setLineSpacing(0,1.3f); addresses.setPadding(0,dp(4),0,0); dashL.addView(addresses);
        output=new TextView(this); output.setTextSize(14); output.setLineSpacing(0,1.15f); output.setTextColor(Color.WHITE); output.setPadding(0,dp(6),0,dp(2));
        output.setText("セットアップ完了後はTVを自動再起動し、Mirakurunを起動します。CATVは起動後に「3. CATVをスキャン・局更新」を実行してください。"); dashR.addView(output);
        // ---- Sections (containers first; buttons are created in the original order) ----
        LinearLayout secSetup=new LinearLayout(this); secSetup.setOrientation(LinearLayout.VERTICAL);
        LinearLayout secDaily=new LinearLayout(this); secDaily.setOrientation(LinearLayout.VERTICAL);
        LinearLayout secNetwork=new LinearLayout(this); secNetwork.setOrientation(LinearLayout.VERTICAL);
        LinearLayout secMaint=new LinearLayout(this); secMaint.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout[] sections={secSetup,secDaily,secNetwork,secMaint};
        final Button[] tiles=new Button[4];
        LinearLayout grid=new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL); body.addView(grid);
        LinearLayout grow1=new LinearLayout(this); grow1.setOrientation(LinearLayout.HORIZONTAL); grid.addView(grow1);
        LinearLayout grow2=new LinearLayout(this); grow2.setOrientation(LinearLayout.HORIZONTAL); grid.addView(grow2);
        String[] catNames={"セットアップ","ふだんの操作","ネットワーク","メンテナンス"};
        for(int i=0;i<4;i++){ final int idx=i; tiles[i]=tileButton(idx<2?grow1:grow2,catNames[i],()->{userChoseSection=true;showSection(sections,tiles,idx);}); }
        for(final Button t:tiles)t.setOnFocusChangeListener((v,f)->refreshTiles(tiles));
        for(int i=0;i<4;i++){ body.addView(sections[i]); sections[i].setVisibility(i==0?View.VISIBLE:View.GONE); }
        refreshTiles(tiles);
        sectionArr=sections; tileArr=tiles;
        // buttons.get(0) must stay the network button (networkOperation uses it)
        Button network=button("ネットワーク設定 / .local名・固定IP");secNetwork.addView(network);network.setOnClickListener(v->NetworkSettings.show(this));networkRefresh.postDelayed(refreshNetwork,5000);
        Button host=button(".localの接続名だけ変更");secNetwork.addView(host);host.setOnClickListener(v->NetworkSettings.showName(this));
        Button setupButton=add(secSetup,"1. セットアップ / 局更新（完了後に自動再起動）",1,()-> {
            String result=Installer.configure(this);
            TclPolicy.configure(this,true);
            getSharedPreferences(NetworkService.PREFS,0).edit().putBoolean("enabled",true).commit();
            startForegroundService(new Intent(this,NetworkService.class));
            Root.capture("touch "+Installer.BASE+"/enabled");
            Root.capture("nohup /system/bin/sh -c '/system/bin/sleep 5; /system/bin/sync; /system/bin/reboot' </dev/null >/dev/null 2>&1 &");
            rebootScheduled=true;
            return result+"\n5秒後にTVを自動再起動します。再起動後はMirakurunを自動起動します。";
        });
        add(secSetup,"2. 起動・自動起動を有効化",0,()-> {
            String result=Installer.command("start");
            TclPolicy.configure(this,true);
            Root.capture("touch "+Installer.BASE+"/enabled");
            getSharedPreferences(NetworkService.PREFS,0).edit().putBoolean("enabled",true).commit();
            startForegroundService(new Intent(this,NetworkService.class));
            Installer.waitReady();
            return result+"\n"+urls();
        });
        add(secSetup,"3. CATVをスキャン・局更新",0,()->Installer.scanCatv(this,text->runOnUiThread(()->{if(!isFinishing())output.setText(text);})));
        Button satellite=button("");secSetup.addView(satellite);
        Runnable refreshSatellite=()->satellite.setText("衛星（BS/CS）もCATVと併用： "+(Installer.satelliteWithCatv(this)?"する":"しない"));
        refreshSatellite.run();
        satellite.setOnClickListener(v->{
            Installer.setSatelliteWithCatv(this,!Installer.satelliteWithCatv(this));refreshSatellite.run();
            execute(satellite,"局一覧を更新しています…（Mirakurunを停止して数十秒で再開します）",()->Installer.refreshChannels(this)+"\n「しない」では、TV本体の衛星スキャンの局（アンテナがないと受信できず、CATV経由のBSと局番号が重なります）を除きます。実際のBS/CSアンテナも使う場合は「する」にしてください。");
        });
        note(secSetup,"CATVは同軸を地デジ端子へ（BS/CS端子は非対応）。初回はMagiskのroot許可が必要です。");
        add(secDaily,"状態を確認",0,()->Installer.command("status"));
        add(secDaily,"ログを表示",0,()->Root.capture("tail -n 60 "+Installer.BASE+"/server.log"));
        add(secDaily,"局一覧だけ更新（スキャンなし）",0,()->Installer.refreshChannels(this));
        add(secMaint,"停止・自動起動を無効化",0,()-> {
            Root.capture("rm -f "+Installer.BASE+"/enabled");
            String result=Installer.command("stop");
            getSharedPreferences(NetworkService.PREFS,0).edit().putBoolean("enabled",false).commit();
            stopService(new Intent(this,NetworkService.class));
            TclPolicy.configure(this,false);
            return result;
        });
        Button coreUpdate=button("Mirakurun本体の更新を確認");secMaint.addView(coreUpdate);
        coreUpdate.setOnClickListener(v->execute(coreUpdate,"Mirakurun本体と公式npmの更新情報を確認しています…",()->{
            CoreUpdate.Info info=CoreUpdate.check(this);
            runOnUiThread(()->{
                if(isFinishing())return;
                String note=!info.online?"更新情報を取得できません。インターネット接続を確認してください。前の本体がある場合はオフラインで戻せます。":info.newer()?(info.supported()?"新しい本体があります。TVのメモリーを確保するため、更新中は配信を停止します。視聴・録画中は適用しません。\n設定・番組DBをバックアップし、起動確認に失敗した場合は元へ戻します。更新中はTVの電源を切らないでください。":"このメジャーバージョンは未対応です。アプリの対応版をお待ちください。") : "本体は最新です。";
                AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Mirakurun本体の更新").setMessage(info.summary()+"\n\n"+note).setNegativeButton("閉じる",null);
                if(info.newer()&&info.supported())dialog.setPositiveButton("本体を更新する",(d,w)->execute(coreUpdate,"Mirakurun本体を更新しています。更新中は配信を停止します。数分かかることがあります。TVの電源を切らないでください。",()->CoreUpdate.install(this,info)));
                if(info.previous!=null)dialog.setNeutralButton("前の本体に戻す",(d,w)->new AlertDialog.Builder(this).setTitle("Mirakurun "+info.previous+" に戻しますか？").setMessage("本体と、前の本体に対応する設定・番組DBの保存状態へ戻します。視聴・録画中は実行できません。").setNegativeButton("キャンセル",null).setPositiveButton("戻す",(dd,ww)->execute(coreUpdate,"前のMirakurun本体と設定・番組DBへ戻しています…",()->CoreUpdate.rollback(this))).show());
                dialog.show();
            });
            return info.summary();
        }));
        add(secMaint,"Mirakurun本体・依存パッケージのライセンス",0,()->CoreUpdate.licenses());
        add(secMaint,"本体更新のログを表示",0,()->Root.capture("tail -n 100 "+Installer.BASE+"/core-update.log"));
        Button remove=button("環境を削除",2); secMaint.addView(remove);
        remove.setOnClickListener(view->new AlertDialog.Builder(this)
            .setTitle("Mirakurun環境を削除しますか？")
            .setMessage("Mirakurun本体・局設定・番組DB・ログ・自動起動を削除します。既存のBonDriver環境は保持します。このアプリが導入したチューナー設定は復元します。アプリ自体は残ります。")
            .setNegativeButton("キャンセル",null)
            .setPositiveButton("環境を削除",(dialog,which)->execute(remove,()-> {
                Root.capture("rm -f "+Installer.BASE+"/enabled");
                getSharedPreferences(NetworkService.PREFS,0).edit().putBoolean("enabled",false).commit();
                stopService(new Intent(this,NetworkService.class));
                NetworkSettings.root(this,"restore-original");
                TclPolicy.configure(this,false);
                return Installer.remove(this);
            })).show());
        tiles[0].requestFocus();
        updateServerState();
    }

    private Button tileButton(LinearLayout parent,String label,Runnable click) {
        Button item=new Button(this); item.setText(label);
        item.setTextSize(19); item.setAllCaps(false); item.setFocusable(true); item.setMinHeight(dp(50)); item.setPadding(dp(20),0,dp(20),0);
        item.setTextColor(Color.WHITE); item.setBackground(shape(COL_BTN,16,0,0));
        item.setOnClickListener(v->click.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(dp(3),dp(3),dp(3),dp(3)); item.setLayoutParams(lp);
        parent.addView(item);
        return item;
    }
    private void refreshTiles(Button[] tiles) {
        for(int i=0;i<tiles.length;i++){
            boolean open=i==openSection,foc=tiles[i].hasFocus();
            int bg=open?COL_PRIMARY:(foc?COL_BTN_FOCUS:COL_BTN);
            tiles[i].setBackground(shape(bg,16,(foc||open)?Color.WHITE:0,(foc||open)?2:0));
        }
    }
    private void showSection(LinearLayout[] sections,Button[] tiles,int idx) {
        openSection=idx;
        for(int i=0;i<sections.length;i++)sections[i].setVisibility(i==idx?View.VISIBLE:View.GONE);
        refreshTiles(tiles);
        for(int i=0;i<sections[idx].getChildCount();i++){View c=sections[idx].getChildAt(i);if(c instanceof Button){c.requestFocus();break;}}
    }
    private void showSectionQuiet(int idx) {
        if(sectionArr==null||idx==openSection)return;
        openSection=idx;
        for(int i=0;i<sectionArr.length;i++)sectionArr[i].setVisibility(i==idx?View.VISIBLE:View.GONE);
        refreshTiles(tileArr);
    }
    private String shortAddress() {
        String ip=null;
        try {
            for(NetworkInterface iface:Collections.list(NetworkInterface.getNetworkInterfaces())){
                if(!iface.isUp()||iface.isLoopback())continue;
                for(InetAddress a:Collections.list(iface.getInetAddresses()))
                    if(a instanceof Inet4Address){ if(iface.getName().startsWith("wlan"))ip=a.getHostAddress(); else if(ip==null)ip=a.getHostAddress(); }
            }
        } catch(Exception ignored){}
        StringBuilder sb=new StringBuilder();
        sb.append("接続先：").append(ip==null?"ネットワーク未接続":"http://"+ip+":40772/");
        try { sb.append("\n名前：http://").append(NetworkSettings.load(this).hostname).append(".local:40772/"); } catch(Exception ignored){}
        return sb.toString();
    }
    private void updateServerState() {
        if(serverCheckRunning)return; serverCheckRunning=true;
        worker.execute(()->{
            boolean up=false;
            try {
                HttpURLConnection conn=(HttpURLConnection)new URL("http://127.0.0.1:40772/api/status").openConnection();
                conn.setConnectTimeout(1500); conn.setReadTimeout(1500);
                up=conn.getResponseCode()==200; conn.disconnect();
            } catch(Exception ignored){}
            final boolean running=up;
            runOnUiThread(()->{ serverCheckRunning=false; if(isFinishing()||serverState==null)return;
                android.text.SpannableStringBuilder sb=new android.text.SpannableStringBuilder();
                sb.append("Mirakurun：");
                int s=sb.length(); sb.append("●");
                sb.setSpan(new android.text.style.ForegroundColorSpan(running?COL_OK:COL_OFF),s,s+1,0);
                sb.append(running?" 配信中（ポート40772）":" 停止中");
                serverState.setText(sb);
                if(!userChoseSection&&!autoSectionDone){autoSectionDone=true;showSectionQuiet(running?1:0);}
            });
        });
    }

    // style: 0=normal, 1=primary, 2=danger
    private Button button(String label) { return button(label,0); }
    private Button button(String label,int style) {
        final int base=style==1?COL_PRIMARY:(style==2?COL_DANGER:COL_BTN);
        final int focus=style==1?COL_PRIMARY_FOCUS:(style==2?COL_DANGER_FOCUS:COL_BTN_FOCUS);
        Button item=new Button(this); item.setText(label); buttons.add(item);
        item.setTextSize(18); item.setAllCaps(false); item.setFocusable(true); item.setMinHeight(dp(48)); item.setPadding(dp(20),0,dp(20),0);
        item.setTextColor(Color.WHITE); item.setBackground(shape(base,16,0,0));
        item.setOnFocusChangeListener((view,hasFocus)->((Button)view).setBackground(shape(hasFocus?focus:base,16,hasFocus?Color.WHITE:0,hasFocus?2:0)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(3),0,dp(3)); item.setLayoutParams(lp);
        return item;
    }

    private Button add(LinearLayout body,String label,Operation op) { return add(body,label,0,op); }
    private Button add(LinearLayout body,String label,int style,Operation op) {
        Button button=button(label,style); body.addView(button);
        button.setOnClickListener(view->execute(button,op));
        return button;
    }

    private void execute(Button selected,Operation op) {
        execute(selected,"処理中です。初回セットアップは数分かかる場合があります。",op);
    }

    private void execute(Button selected,String message,Operation op) {
            for(Button item:buttons)item.setEnabled(false);
            output.setText(message);
            worker.execute(()-> {
                String text;
                try { text=op.run(); } catch(Exception e) { text="処理に失敗しました\n"+e.getMessage(); }
                final String result=text;
                runOnUiThread(()-> { if(isFinishing())return; output.setText(result); for(Button item:buttons)item.setEnabled(!rebootScheduled); if(!rebootScheduled)selected.requestFocus(); updateServerState(); });
            });
    }

    private String urls() {
        StringBuilder result=new StringBuilder("接続先（同じLAN内のブラウザーから）\n");
        try {
            for(NetworkInterface iface:Collections.list(NetworkInterface.getNetworkInterfaces()))
                for(InetAddress ip:Collections.list(iface.getInetAddresses()))
                    if(ip instanceof Inet4Address && !ip.isLoopbackAddress()) result.append("http://").append(ip.getHostAddress()).append(":40772/\n");
        }catch(Exception ignored){}
        return result.toString().trim();
    }
    void networkOperation(Operation operation){execute(buttons.get(0),operation);}
    @Override protected void onResume(){super.onResume();updateServerState();}
    @Override public void onDestroy(){networkRefresh.removeCallbacks(refreshNetwork);worker.shutdown();super.onDestroy();}
}
