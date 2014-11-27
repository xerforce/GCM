package com.grokkingandroid.sampleapp.samples.alert;

import com.grokkingandroid.sampleapp.samples.gcm.R;

import android.support.v7.app.ActionBarActivity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class AlertListActivity extends ActionBarActivity {
//	private MyReceiver receiver = null;
	private Intent intent=null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_alert_list);
		
		intent=this.getIntent();
		Bundle bl=intent.getExtras();
		String msg=bl.getString("msg");
		showDialog(msg);

//		receiver = new MyReceiver();
//		IntentFilter filter = new IntentFilter();
//		filter.addAction("com.hmkcode.android.gcm.GcmMessageHandler");
//		AlertListActivity.this.registerReceiver(receiver, filter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.alert_list, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void showDialog(String msg) {
		// TODO Auto-generated method stub
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setTitle("Emergency!!!");
		alertDialogBuilder.setIcon(R.drawable.ic_warning);
		// alertDialogBuilder.setMessage(R.string.partient_warning);
		alertDialogBuilder.setMessage(msg);
		alertDialogBuilder.setPositiveButton("OK",
				new DialogInterface.OnClickListener() {
					@Override
						// TODO Auto-generated method stub
					public void onClick(DialogInterface dialog, int which) {
						Log.e("Dialog", "ok has been clicked");
					}
				});
		alertDialogBuilder.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// TODO Auto-generated method stub
						Log.e("Dialog", "cancel has been clicked");
					}
				});

		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();
	}
	
	
}
