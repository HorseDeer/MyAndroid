package com.example.admin.myandroid;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import twitter4j.AsyncTwitter;
import twitter4j.AsyncTwitterFactory;
import twitter4j.auth.AccessToken;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {
    //各種グローバル変数の定義(twitter連携用)
    AsyncTwitterFactory factory = new AsyncTwitterFactory();
    AsyncTwitter twitter = factory.getInstance();
    AccessToken accessToken;
    final int REQUEST_ACCESS_TOKEN = 0;
    final String consumer_key = "mN3nLNC0DKY1hvrut1rJZVdqG";
    final String consumer_secret = "zeoqUjyvaNTZDiDTbE2ERyw2j7JDTJGqUE6pZHCJLBlbAcIYbJ";
    String token = "", token_secret = "";
    SharedPreferences pref;
    SharedPreferences.Editor editor;

    //主にセンサー関係のグローバル変数(Android画面上の処理用)
    private SensorManager asm,gsm,lsm;
    private LocationManager lm;
    private Button attentionMode;
    boolean endless = false, setDistance = false, isDisTweet = false, isAttention = false, first = true, up;
    float[] data = new float[3];
    float lightS = 0, lightE, gyroZ;
    int vibra = 0, location_min_time = 0, location_min_distance = 1, TLsize = 20, hunger = 30, shake = 0;
    double startLati, endLati, startLong, endLong, distance = 1.0, total = 0.0;
    long start, end, endlessS=0, endlessG=0;
    TextView appMessage, PastTL, TimeIndex;
    ArrayList<String> tMessage = new ArrayList<String>();
    ArrayList<float[]> gyroList = new ArrayList<float[]>();
    static ImageView appFace;

    //画面に出すメッセージデータ
    String[] TenMessage = {"？？？？？？","おやおや？","あれ？反応がないぞ？","動きが止まってるよ～"};
    String[] TwentyMessage = {"おーい！","構って～","暇だよ～","ちょっと寂しくなってきた","今ならまだ間に合う。構って","構うことをお勧めするよ",
            "撫でて～","まさか放置！？"};
    String[] ThirtyMessage = {"か゛ま゛っ゛て゛！！！！","寂しいよ～","ねえねえねえねえ","遊んで～","ほう・・？我を放置するとはいい度胸だ・・・",
            "The HI☆MA","もしかして寝てるの～"};
    String[] touchMessage = {"くすぐったい","もっと触って～","お触りは禁止されています～","Don't touch me!","何ですか～？",
            "力が・・・欲しいか・・・？","貴様、覚悟は出来ているのだろうな・・・？"};
    String[] shakeMessage = {"目が回る～","揺らし過ぎだ～","そんなに振り回すと酔っちゃう・・・","なんだ！？天変地異か！？","吐きそう(小並感)","揺らすのをやめるんだ！マジで！"};

    //音関係の変数の初期化
    private SoundPool sp = null;;
    private int soundID01;
    private int soundID02;
    private int soundID03;
    private int soundID04;
    private int soundID05;

    //アクティビティ生成時に呼び出される
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //各種センサマネージャとロケーションマネージャの設定
        asm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        gsm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        lsm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        lm = (LocationManager)getSystemService(Service.LOCATION_SERVICE);
        //時間計測の開始
        start = System.currentTimeMillis();

        data[0] = 0;
        data[1] = 0;
        data[2] = 0;
        //各種テキストビューの紐づけ
        appFace = (ImageView) findViewById(R.id.face);
        appMessage = (TextView)findViewById(R.id.msg);
        appMessage.setTextColor(Color.BLUE);
        PastTL = (TextView)findViewById(R.id.pastTL);
        attentionMode = (Button)findViewById(R.id.attension);
        TimeIndex = (TextView)findViewById(R.id.index);
        //端末にログインデータを保存する
        pref = getSharedPreferences("t4jdata", Activity.MODE_PRIVATE);
        token=pref.getString("token", "");
        token_secret=pref.getString("token_secret", "");

        twitter.setOAuthConsumer(consumer_key, consumer_secret);
        twitter.getOAuthRequestTokenAsync();

        if(token.length()==0){
            //初回ログイン時の動作
            Intent intent = new Intent(getApplicationContext(), OAuthActivity.class);
            intent.putExtra(OAuthActivity.EXTRA_CONSUMER_KEY, consumer_key);
            intent.putExtra(OAuthActivity.EXTRA_CONSUMER_SECRET, consumer_secret);
            startActivityForResult(intent, REQUEST_ACCESS_TOKEN);
        } else {
            //2回目以降のログイン時の動作
            accessToken = new AccessToken(token, token_secret);
            Intent intent = new Intent(MainActivity.this, TwitterService.class);
            intent.putExtra(TwitterService.EXTRA_CONSUMER_KEY, consumer_key);
            intent.putExtra(TwitterService.EXTRA_CONSUMER_SECRET, consumer_secret);
            intent.putExtra(TwitterService.EXTRA_ACCESS_TOKEN, token);
            intent.putExtra(TwitterService.EXTRA_ACCESS_TOKEN_SECRET, token_secret);
            startService(intent);
        }
    }

    //ログイン関係のメソッド
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ACCESS_TOKEN && resultCode == Activity.RESULT_OK) {
            token = data.getStringExtra(OAuthActivity.EXTRA_ACCESS_TOKEN);
            token_secret = data.getStringExtra(OAuthActivity.EXTRA_ACCESS_TOKEN_SECRET);
            accessToken = new AccessToken(token, token_secret);

            //  accesstokenを記録して2回目以降自動にログインする
            editor = pref.edit();
            editor.putString("token", token);
            editor.putString("token_secret", token_secret);
            editor.commit();

            Intent intent = new Intent(MainActivity.this, TwitterService.class);
            intent.putExtra(TwitterService.EXTRA_CONSUMER_KEY, consumer_key);
            intent.putExtra(TwitterService.EXTRA_CONSUMER_SECRET, consumer_secret);
            intent.putExtra(TwitterService.EXTRA_ACCESS_TOKEN, token);
            intent.putExtra(TwitterService.EXTRA_ACCESS_TOKEN_SECRET, token_secret);
            startService(intent);
        }
    }

    @Override
    protected void onResume() {
        //センサマネージャ等の設定
        super.onResume();
        List<Sensor> sensors = asm.getSensorList(Sensor.TYPE_ACCELEROMETER);
        if (sensors.size() > 0) {
            Sensor s = sensors.get(0);
            asm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        }
        List<Sensor> sensors2 = gsm.getSensorList(Sensor.TYPE_GYROSCOPE);
        if (sensors2.size() > 0) {
            Sensor s2 = sensors2.get(0);
            gsm.registerListener(this, s2, SensorManager.SENSOR_DELAY_NORMAL);
        }
        List<Sensor> sensors3 = gsm.getSensorList(Sensor.TYPE_LIGHT);
        if (sensors3.size() > 0) {
            Sensor s3 = sensors3.get(0);
            gsm.registerListener(this, s3, SensorManager.SENSOR_DELAY_NORMAL);
        }
        boolean isNetworkEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (isNetworkEnabled) {
            //警告文が出るが、これは「ユーザに拒否されるかもよ？」という意味なので実行には問題ない
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, location_min_time, location_min_distance, this);
        }

        //効果音
        sp = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        soundID01 = sp.load(this,R.raw.catvoice1,1);
        soundID02 = sp.load(this,R.raw.catvoice2,1);
        soundID03 = sp.load(this,R.raw.catvoice3,1);
        soundID04 = sp.load(this,R.raw.catvoice4,1);
        soundID05 = sp.load(this,R.raw.catvoice5,1);
    }

    //GPSの位置情報が変わった時に呼び出される
    @Override
    public void onLocationChanged(Location location) {
        if (TwitterService.isLogin) {
            if (!setDistance) {
                //移動前の位置の記録
                startLati = location.getLatitude(); //緯度の取得
                startLong = location.getLongitude();//経度の取得
                setDistance = true;
            }
            //現在地の記録
            endLati = location.getLatitude();
            endLong = location.getLongitude();
            //計算式　=　√( (緯度の差 * 111)**２ + (経度の差 * 91)**２ )
            if (setDistance) {
                //現在地と初期値の距離を計算する
                double latiDis = ((endLati - startLati) * 111.0) * ((endLati - startLati) * 111.0);
                double longDis = ((endLong - startLong) * 91.0) * ((endLong - startLong) * 91.0);
                if (Math.sqrt(latiDis + longDis) >= 0.01 && !isDisTweet) {
                    //初期値からの移動距離が一定以上だったら「移動した」とみなす
                    total += Math.sqrt(latiDis + longDis);
                    //初期位置のリセット
                    startLati = endLati;
                    startLong = endLong;
                    if (tMessage.size() > TLsize) {
                        tMessage.remove(0);
                    }
                    tMessage.add("今だいたい" + String.format("%.3f", total) + "km移動したよー");
                    if (total >= distance) {
                        //トータルで一定以上移動してたらツイート
                        Intent intent = new Intent(MainActivity.this, TwitterService.class);
                        intent.putExtra(TwitterService.EXTRA_isTweet, true);
                        intent.putExtra(TwitterService.EXTRA_tweet, distance + "km移動したー");
                        startService(intent);
                        distance += 1.0;
                    }
                }
            }
            if (tMessage.size() != 0) {
                String TL = "";
                for (int i = 0; i < tMessage.size(); i++) {
                    if (i != tMessage.size() - 1) {
                        TL += tMessage.get(i) + "\n";
                    }
                }
                PastTL.setText(TL);
                appMessage.setText(tMessage.get(tMessage.size() - 1));
            }
        }
    }

    //端末のGPSの状態が変わると呼び出される
    @Override
    public void onStatusChanged(String provider, int status, Bundle bundle) {
        switch (status) {
            case LocationProvider.OUT_OF_SERVICE:
                if (tMessage.size() != 0 && !tMessage.get(tMessage.size()-1).equals(provider+"が圏外になってて利用できないよー")) {
                    if (tMessage.size() > TLsize) {
                        tMessage.remove(0);
                    }
                    tMessage.add(provider+"が圏外になってて利用できないよー");
                }
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                if (tMessage.size() != 0 && !tMessage.get(tMessage.size()-1).equals("一時的に"+provider+"が利用できなくなってるー")) {
                    if (tMessage.size() > TLsize) {
                        tMessage.remove(0);
                    }
                    tMessage.add("一時的に"+provider+"が利用できなくなってるー");
                }
                break;
            case LocationProvider.AVAILABLE:
                if (tMessage.size() != 0 && !tMessage.get(tMessage.size()-1).equals(provider+"は利用できるよー")) {
                    if (tMessage.size() > TLsize) {
                        tMessage.remove(0);
                    }
                    tMessage.add(provider+"は利用できるよー");
                }
                break;
        }
        if (tMessage.size() != 0) {
            String TL = "";
            for (int i = 0; i < tMessage.size(); i++) {
                if (i != tMessage.size()-1) {
                    TL += tMessage.get(i) + "\n";
                }
            }
            PastTL.setText(TL);
            appMessage.setText(tMessage.get(tMessage.size()-1));
        }
    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    //センサーが値を取得すると呼び出される
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (TwitterService.isLogin) {
            float x = 0, y = 0, z = 0;
            switch (sensorEvent.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    x = sensorEvent.values[0];
                    y = sensorEvent.values[1];
                    z = sensorEvent.values[2];
                    if (Math.abs(data[0] - x) > 0.2 || Math.abs(data[1] - y) > 0.2 || Math.abs(data[2] - z) > 0.2) {
                        //加速度に一定以上の変化があった場合
                        data[0] = x;
                        data[1] = y;
                        data[2] = z;
                        start = System.currentTimeMillis();
                        vibra = 0;
                    }
                    end = System.currentTimeMillis();
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    //ジャイロの値から振りの回数を大まかに記録する
                    if (first) {
                        first = false;
                        up = true;
                        gyroZ = sensorEvent.values[2];
                    } else {
                        if (up && sensorEvent.values[2] < gyroZ && sensorEvent.values[2] < -2) {
                            up = false;
                            shake++;
                        } else if (!up && sensorEvent.values[2] > gyroZ && sensorEvent.values[2] > 2) {
                            up = true;
                        }
                        gyroZ = sensorEvent.values[2];
                    }
                    break;
                case Sensor.TYPE_LIGHT:
                    if (lightS == 0) lightS = sensorEvent.values[0];
                    lightE = sensorEvent.values[0];
                    if (lightS <= 40 && lightE >= 600) {
                        if (tMessage.size() > TLsize) {
                            tMessage.remove(0);
                        }
                        tMessage.add("まぶしい！");
                        Intent intent = new Intent(MainActivity.this, TwitterService.class);
                        intent.putExtra(TwitterService.EXTRA_isTweet, true);
                        intent.putExtra(TwitterService.EXTRA_tweet, "うおっまぶし！");
                        startService(intent);
                        lightS = lightE;
                    }
                    if (lightE >= 30000) {
                        if (tMessage.size() != 0 && !tMessage.get(tMessage.size() - 1).equals("目がぁぁぁぁっ！！！！")) {
                            if (tMessage.size() > TLsize) {
                                tMessage.remove(0);
                            }
                            tMessage.add("目がぁぁぁぁっ！！！！");
                            sp.play(soundID05, 1, 1, 0, 0, 1);
                        }
                        Intent intent = new Intent(MainActivity.this, TwitterService.class);
                        intent.putExtra(TwitterService.EXTRA_isTweet, true);
                        intent.putExtra(TwitterService.EXTRA_tweet, "おでかけだ！");
                        startService(intent);
                        lightS = lightE;
                    }
                    if (lightE <= 40) {
                        if (tMessage.size() != 0 && !tMessage.get(tMessage.size() - 1).equals("ZZZZZ.....")) {
                            if (tMessage.size() > TLsize) {
                                tMessage.remove(0);
                            }
                            tMessage.add("ZZZZZ.....");
                        }
                        Intent intent = new Intent(MainActivity.this, TwitterService.class);
                        intent.putExtra(TwitterService.EXTRA_isTweet, true);
                        intent.putExtra(TwitterService.EXTRA_tweet, "暗くて眠くなってきた・・・");
                        startService(intent);
                        lightS = lightE;
                    }
                    break;
            }
            //かまってちゃんモードがONになっている場合
            if (isAttention) {
                TimeIndex.setText((end - start) / 1000 + " 秒");
                if ((end - start) / 1000 >= 10 && vibra == 0) {
                    //もし一定以上加速度が変わらなかった場合、バイブレーションを起動する
                    ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(new long[]{500, 200, 500, 200}, -1);
                    vibra = 1;
                    if (tMessage.size() > TLsize) {
                        tMessage.remove(0);
                    }
                    tMessage.add(TenMessage[(int) (Math.random() * TenMessage.length)]);
                    //twiterで呟く
                    Intent intent = new Intent(MainActivity.this, TwitterService.class);
                    intent.putExtra(TwitterService.EXTRA_isTweet, true);
                    intent.putExtra(TwitterService.EXTRA_tweet, "ちょっと放置されてる(10秒)");
                    startService(intent);
                    //音楽(効果音)を鳴らす
                    sp.play(soundID01, 1, 1, 0, 0, 1);
                }
                if ((end - start) / 1000 >= 20 && vibra == 1) {
                    //さらに一定以上加速度が変わらなかった場合、バイブレーションを起動する
                    ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(new long[]{500, 600, 500, 600}, -1);
                    vibra = 2;
                    if (tMessage.size() > TLsize) {
                        tMessage.remove(0);
                    }
                    tMessage.add(TwentyMessage[(int) (Math.random() * TwentyMessage.length)]);
                    Intent intent = new Intent(MainActivity.this, TwitterService.class);
                    intent.putExtra(TwitterService.EXTRA_isTweet, true);
                    intent.putExtra(TwitterService.EXTRA_tweet, "さらに放置されてる(20秒)");
                    startService(intent);
                    sp.play(soundID02, 1, 1, 0, 0, 1);
                }
                if ((end - start) / 1000 >= 30) {
                    //さらにさらに一定以上加速度が変わらなかった場合、バイブレーションを永続起動する
                    if (!endless) {
                        endlessS = System.currentTimeMillis();
                        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(1000);
                        if (tMessage.size() > TLsize) {
                            tMessage.remove(0);
                        }
                        tMessage.add(ThirtyMessage[(int) (Math.random() * ThirtyMessage.length)]);
                        Intent intent = new Intent(MainActivity.this, TwitterService.class);
                        intent.putExtra(TwitterService.EXTRA_isTweet, true);
                        intent.putExtra(TwitterService.EXTRA_tweet, "見放されてる。つらい(30秒)");
                        startService(intent);
                        sp.play(soundID01, 1, 1, 0, 0, 1);
                    }
                    endlessG = System.currentTimeMillis();
                    if ((endlessG - endlessS) < 700) endless = true;
                    else endless = false;
                }
            }
            if (shake == 10) {
                //一定数振られていたらメッセージを出す
                if (tMessage.size() > TLsize) {
                    tMessage.remove(0);
                }
                tMessage.add(shakeMessage[(int) (Math.random() * shakeMessage.length)]);
                shake = 0;
                Intent intent = new Intent(MainActivity.this, TwitterService.class);
                intent.putExtra(TwitterService.EXTRA_isTweet, true);
                intent.putExtra(TwitterService.EXTRA_tweet, shakeMessage[(int) (Math.random() * shakeMessage.length)]);
                startService(intent);
                sp.play(soundID03, 1, 1, 0, 0, 1);
            }
            //決まった時間帯にメッセージを表示する
            Calendar cal = Calendar.getInstance();
            int iHour = cal.get(Calendar.HOUR);         //時を取得
            int iMinute = cal.get(Calendar.MINUTE);     //分を取得
            int iAM_PM = cal.get(Calendar.AM_PM);       //AM or PMを取得 AM = 0, PM = 1
            if (iAM_PM == 1 && iHour == 0 && hunger % 2 == 0) {
                if (tMessage.size() > TLsize) {
                    tMessage.remove(0);
                }
                tMessage.add("お腹がグーグーへりんこファイヤー（涙）");
                Intent intent = new Intent(MainActivity.this, TwitterService.class);
                intent.putExtra(TwitterService.EXTRA_isTweet, true);
                intent.putExtra(TwitterService.EXTRA_tweet, "お腹がグーグーへりんこファイヤー（涙）");
                startService(intent);
                hunger = hunger / 2;
            } else if (iAM_PM == 1 && iHour == 7 && hunger % 3 == 0) {
                if (tMessage.size() > TLsize) {
                    tMessage.remove(0);
                }
                tMessage.add("お腹がグーグーへりんこファイヤー（涙）");
                Intent intent = new Intent(MainActivity.this, TwitterService.class);
                intent.putExtra(TwitterService.EXTRA_isTweet, true);
                intent.putExtra(TwitterService.EXTRA_tweet, "お腹がグーグーへりんこファイヤー（涙）");
                startService(intent);
                hunger = hunger / 3;
            } else if (iAM_PM == 0 && iHour == 7 && hunger % 5 == 0) {
                if (tMessage.size() > TLsize) {
                    tMessage.remove(0);
                }
                tMessage.add("お腹がグーグーへりんこファイヤー（涙）");
                Intent intent = new Intent(MainActivity.this, TwitterService.class);
                intent.putExtra(TwitterService.EXTRA_isTweet, true);
                intent.putExtra(TwitterService.EXTRA_tweet, "お腹がグーグーへりんこファイヤー（涙）");
                startService(intent);
                hunger = hunger / 5;
            }
            if (tMessage.size() != 0) {
                String TL = "";
                for (int i = 0; i < tMessage.size(); i++) {
                    if (i != tMessage.size() - 1) {
                        TL += tMessage.get(i) + "\n";
                    }
                }
                PastTL.setText(TL);
                appMessage.setText(tMessage.get(tMessage.size() - 1));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    protected void onDestroy() {
        //センサマネージャの解放
        super.onDestroy();
        asm.unregisterListener(this);
    }

    //ボタンが押された時に呼び出される
    public void attentionSeeker(View view) {
        if (isAttention) {
            isAttention = false;
            attentionMode.setText("ON");
            vibra = 0;
            TimeIndex.setText("");
        }
        else {
            isAttention = true;
            start = System.currentTimeMillis();
            attentionMode.setText("OFF");
        }
    }

    //アイコンがタップされると呼び出される
    public void faceClick(View view) {
        if (tMessage.size() > TLsize) {
            tMessage.remove(0);
        }
        tMessage.add(touchMessage[(int)(Math.random()*touchMessage.length)]);
        start = System.currentTimeMillis();
        vibra = 0;
        TimeIndex.setText("");
        sp.play(soundID04,1,1,0,0,1);
    }
}
