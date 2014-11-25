package com.test.ping;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import android.os.AsyncTask;
import android.widget.Button;
import android.widget.TextView;


public class PingTask extends AsyncTask<String, PingTask.PingResult, Integer> {
	
	class PingResult
	{
		boolean mIsSuccessfull;
		long mPingTime;
		String mText;
	}
	
	private final int PING_PORT = 80; 
	private final int PING_COUNT = 10; 
	
	private TextView tvReport;
	private TextView btnPing;
	
	public PingTask(TextView report, Button ping)
	{
		tvReport = report;
		btnPing = ping;
	}
	
	@Override
	protected void onPreExecute()
	{
		super.onPreExecute();
		btnPing.setText(btnPing.getContext().getResources().getString(R.string.stop));
	}

	@Override
	protected Integer doInBackground(String... urls) {
		String url = urls[0];
		InetAddress address;
		PingResult res;
		int pingTimeSum = 0;
		
		try {
			address = InetAddress.getByName(url);
		} catch (UnknownHostException e) {
			res = new PingResult();
			res.mIsSuccessfull = false;
			res.mText = e.getMessage();
			publishProgress(res); 
			return 0;
		}
		res = new PingResult();
		res.mIsSuccessfull = false;
		res.mText = "Ping " + address.getHostAddress();
		publishProgress(res); 
		for (int i = 0; i < PING_COUNT; ++i)
		{
			if (isCancelled())
			{
				btnPing.setText(btnPing.getContext().getResources().getString(R.string.ping));
				return 0;
			}
			try {
				Socket s = new Socket();
				long startTime = System.currentTimeMillis();
				s.connect(new InetSocketAddress(address, PING_PORT), 10000);
				long endTime = System.currentTimeMillis();
				res = new PingResult();
				res.mIsSuccessfull = true;
				res.mPingTime = endTime - startTime;
				publishProgress(res);
				s.close();
				pingTimeSum += endTime - startTime;
			} catch (IOException e) {
				res = new PingResult();
				res.mIsSuccessfull = false;
				res.mText = e.getMessage();
				publishProgress(res); 
			}
			if (isCancelled())
			{
				btnPing.setText(btnPing.getContext().getResources().getString(R.string.ping));
				return 0;
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				res = new PingResult();
				res.mIsSuccessfull = false;
				res.mText = e.getMessage();
				publishProgress(res); 
			}
		}
		return pingTimeSum / PING_COUNT;
	}
	
    @Override
    protected void onProgressUpdate(PingTask.PingResult... values) 
    {
    	super.onProgressUpdate(values);
    	if (values[0].mIsSuccessfull)
    	{
    		tvReport.append(values[0].mPingTime + " ms\n");    		
    	}
    	else
    	{
    		tvReport.append(values[0].mText + "\n");
    	}
    	scrollToBottom();
    }
    
    @Override
    protected void onPostExecute(Integer result) {
		super.onPostExecute(result);
		btnPing.setText(btnPing.getContext().getResources().getString(R.string.ping));
		if (result == 0)
			return;
		tvReport.append(tvReport.getContext().getResources().getString(R.string.average) + ": " + result.toString() + " ms\n");
	   	scrollToBottom();
    }

	private void scrollToBottom()
	{
	    final int scrollAmount = tvReport.getLayout().getLineTop(tvReport.getLineCount()) - tvReport.getHeight();
	    if (scrollAmount > 0)
	    	tvReport.scrollTo(0, scrollAmount);
	    else
	    	tvReport.scrollTo(0, 0);
	}
	

}
