package com.test.ping;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	
	private final String PARAMS_NAME = "Ping.prefs";
	private final String PARAM_HOSTS = "hosts";
	
	private final String IP_REGEX = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";
	private final String HOST_REGEX = "^([a-zA-Z]|[a-zA-Z][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])(\\.([a-zA-Z]|[a-zA-Z][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9]))*$";

	private TextView tvSelfAddress;
	private TextView tvReport;
	private AutoCompleteTextView tvHostAddress;
	private Button btnPing;
	
	private BroadcastReceiver mConnectionChangeReceiver;
	private PingTask mPingTask;
	Set<String> mStoredHosts;
	ColorStateList mDefaultTextColor;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		tvSelfAddress = (TextView) findViewById(R.id.tvSelfAddress);
		tvReport = (TextView) findViewById(R.id.tvReport);
		tvReport.setMovementMethod(new ScrollingMovementMethod());
		tvHostAddress = (AutoCompleteTextView) findViewById(R.id.tvHostAddress);
		mDefaultTextColor = tvHostAddress.getTextColors();
		tvHostAddress.addTextChangedListener(new TextWatcher(){
			@Override
			public void afterTextChanged(Editable text) {
				if (!text.toString().matches(IP_REGEX) && !text.toString().matches(HOST_REGEX))
					tvHostAddress.setTextColor(Color.rgb(127, 0, 0));
				else
					tvHostAddress.setTextColor(mDefaultTextColor);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}});
		
		btnPing = (Button) findViewById(R.id.btnPing);
		btnPing.setOnClickListener(new OnClickListener() 
		{
			@Override
			public void onClick(View v) 
			{
				// Если пинг уже запущен
				if (mPingTask != null && mPingTask.getStatus() == AsyncTask.Status.RUNNING)
				{
					// Прерываем
					mPingTask.cancel(true);
					mPingTask = null;
					tvReport.append(getResources().getString(R.string.cancelled) + "\n");
				}
				// Если пинг не запущен
				else
				{
					// Проверяем имя хоста
					String host = tvHostAddress.getText().toString();
					if (host.isEmpty())
					{
						Toast.makeText(getApplicationContext(), "Enter host name or IP address", Toast.LENGTH_SHORT).show();
						return;
					}
					if (!host.matches(IP_REGEX) && !host.matches(HOST_REGEX))
					{
						Toast.makeText(getApplicationContext(), "Invalid host name", Toast.LENGTH_SHORT).show();
						return;						
					}
					addNewHost(host);
					tvReport.setText("");
					mPingTask = new PingTask(tvReport, btnPing);
					mPingTask.execute(host);
				}
			}
		});
		
		// Сюда будет приходить информация о смене состояния сетевого подключения
		mConnectionChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) 
            {
            	displayNetworkInfo();
            }
        };
        registerReceiver(mConnectionChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        loadHosts();

		displayNetworkInfo();
	}
	
	private void loadHosts()
	{
		// Загружаем список сохраненных хостов
		SharedPreferences prefs = getSharedPreferences(PARAMS_NAME, MODE_PRIVATE);
		mStoredHosts = prefs.getStringSet(PARAM_HOSTS, new HashSet<String>());
		String[] hostsArray = {};
		hostsArray = (String[])mStoredHosts.toArray(new String[]{});
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, hostsArray);
		tvHostAddress.setAdapter(adapter);		
	}
	
	private void addNewHost(String host)
	{
		// Добавляем новый хост к списку сохраненных хостов
		if (mStoredHosts == null)
			mStoredHosts = new HashSet<String>();
		// Копируем объект, т.к. иначе потом он не сохраняется
		mStoredHosts = new HashSet<String>(mStoredHosts);
		mStoredHosts.add(host);
		getSharedPreferences(PARAMS_NAME, MODE_PRIVATE).edit().putStringSet(PARAM_HOSTS, mStoredHosts).commit();
		String[] hostsArray = (String[])mStoredHosts.toArray(new String[]{});
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, hostsArray);
		tvHostAddress.setAdapter(adapter);		
	}
	
	private String int2IpString(int ip)
	{
		return (ip & 0xFF ) + "." +
			   ((ip >> 8) & 0xFF) + "." +
			   ((ip >> 16) & 0xFF) + "." +
			   ((ip >> 24) & 0xFF);
	}
	
	private void displayNetworkInfo()
	{
		ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		// Проверяем, есть ли сетевое подключение
		NetworkInfo info = cm.getActiveNetworkInfo();
		if (info == null)
		{
			tvSelfAddress.setTextColor(Color.rgb(127, 0, 0));
			tvSelfAddress.setText(getResources().getString(R.string.no_connection));
			return;
		}

		// Если подключены через мобильную сеть
		if (info.getType() == ConnectivityManager.TYPE_MOBILE)
		{
			// Получаем только IP-адрес
			String ipAddress = null;
		    try {
		        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
		            NetworkInterface intf = en.nextElement();
		            for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
		                InetAddress inetAddress = enumIpAddr.nextElement();
		                if (!inetAddress.isLoopbackAddress()) {
		                    ipAddress = inetAddress.getHostAddress().toString();
							tvSelfAddress.setTextColor(Color.rgb(0, 127, 0));
		                    tvSelfAddress.setText("DNS: N/A\nGateway: N/A\nIP address: " + ipAddress);
		                }
		            }
		        }
		    } catch (SocketException ex) {}
		}
		// Проверим Wi-Fi подключение
		else
		{
			NetworkInfo wifiNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			// Если есть Wi-Fi подключение
			if (wifiNetwork != null && wifiNetwork.isConnected())
			{
		        WifiManager wifi = (android.net.wifi.WifiManager)getSystemService(Context.WIFI_SERVICE);
		        DhcpInfo dhcp = wifi.getDhcpInfo();
		        // Если DHCP информацию получить не удалось
		        if (dhcp == null)
		        {
		        	// Сообщаем об этом
					tvSelfAddress.setTextColor(Color.rgb(127, 0, 0));
		        	tvSelfAddress.setText(getResources().getString(R.string.no_dhcp));
		        }
		        // Если информация получена
		        else
		        {
		        	// Отображаем текущие DNS, Gateway, IP
					tvSelfAddress.setTextColor(Color.rgb(0, 127, 0));
					tvSelfAddress.setText("DNS: " + int2IpString(dhcp.dns1) + ", " + int2IpString(dhcp.dns1) + 
							  	"\nGateway: " + int2IpString(dhcp.gateway) +
							  	"\nIP address: " + int2IpString(dhcp.ipAddress));
		        }
			}
			// Если Wi-Fi отключен
	        else
	        {
				tvSelfAddress.setTextColor(Color.rgb(127, 0, 0));
	        	tvSelfAddress.setText("Cannot display current network parameters: no connection!");
	        }
		}
	}
	
	@Override
	protected void onDestroy()
	{
		unregisterReceiver(mConnectionChangeReceiver);
		super.onDestroy();
	}

}
