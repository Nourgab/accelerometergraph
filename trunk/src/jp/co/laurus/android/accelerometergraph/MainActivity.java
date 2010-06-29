package jp.co.laurus.android.accelerometergraph;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 
 * @author okumura.laurus@gmail.com
 * 
 */
public class MainActivity extends Activity {

	private static final String TAG = "Accelerometer Graph";

	private static final int DATA_R = 3;

	private static final int STATUS_START = 1;
	private static final int STATUS_STOP = 2;

	private static final int PASS_FILTER_RAW = 0;
	private static final int PASS_FILTER_LOW = 1;
	private static final int PASS_FILTER_HIGH = 2;

	private static final int MENU_SENSOR_DELAY = (Menu.FIRST + 1);
	private static final int MENU_START_SAVE = (Menu.FIRST + 2);
	private static final int MENU_SAVE = (Menu.FIRST + 3);
	private static final int MENU_END = (Menu.FIRST + 4);

	private static final int DIALOG_SAVE_PROGRESS = 0;

	private float[] mCurrents = new float[4];
	private ConcurrentLinkedQueue<float[]> mHistory = new ConcurrentLinkedQueue<float[]>();
	private ConcurrentLinkedQueue<float[]> mRawHistory = new ConcurrentLinkedQueue<float[]>();
	private TextView[] mAccValueViews = new TextView[4];
	private float[] mLowPassFilters = { 0.0f, 0.0f, 0.0f, 0.0f };
	private boolean[] mGraphs = { true, true, true, true };
	private int[] mAngleColors = new int[4];

	private int mBGColor;
	private int mZeroLineColor;
	private int mStringColor;

	private GraphView mGraphView;
	private TextView mFilterRateView;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;

	private int mSensorDelay = SensorManager.SENSOR_DELAY_UI;
	private int mMaxHistorySize;
	private boolean mDrawRoop = true;
	private int mDrawDelay = 100;
	private int mLineWidth = 2;
	private int mGraphScale = 6;
	private int mZeroLineY = 230;
	private int mZeroLineYOffset = 0;
	private float mTouchOffset;
	private int mStatus = STATUS_START;
	private int mPassFilter = PASS_FILTER_RAW;
	private float mFilterRate = 0.1f;
	private boolean mRecording = false;

	private SensorEventListener mSensorEventListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			Log.i(TAG, "mSensorEventListener.onAccuracyChanged()");
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			Log.i(TAG, "mSensorEventListener.onSensorChanged()");

			// RAW履歴を登録
			if (mRecording) {
				mRawHistory.add(event.values.clone());
			}

			for (int angle = 0; angle < 3; angle++) {
				float value = event.values[angle];
				// ローパスフィルタを作成
				mLowPassFilters[angle] = (mLowPassFilters[angle] * (1 - mFilterRate))
						+ (value * mFilterRate);
				// フィルタをかける
				switch (mPassFilter) {
				case PASS_FILTER_LOW:
					value = mLowPassFilters[angle];
					break;
				case PASS_FILTER_HIGH:
					value -= mLowPassFilters[angle];
					break;
				}
				mCurrents[angle] = value;
				mAccValueViews[angle].setText(String.valueOf(value));
			}

			// 実加速度の計算
			Double dReal = new Double(Math.abs(Math.sqrt(Math.pow(
					event.values[SensorManager.DATA_X], 2)
					+ Math.pow(event.values[SensorManager.DATA_Y], 2)
					+ Math.pow(event.values[SensorManager.DATA_Z], 2))));
			float fReal = dReal.floatValue();
			// ローパスフィルタを作成
			mLowPassFilters[DATA_R] = (mLowPassFilters[DATA_R] * (1 - mFilterRate))
					+ (fReal * mFilterRate);
			// フィルタをかける
			switch (mPassFilter) {
			case PASS_FILTER_LOW:
				fReal = mLowPassFilters[DATA_R];
				break;
			case PASS_FILTER_HIGH:
				fReal -= mLowPassFilters[DATA_R];
				break;
			}
			mCurrents[DATA_R] = fReal;
			mAccValueViews[DATA_R].setText(String.valueOf(fReal));

			synchronized (this) {
				// 履歴を登録
				if (mHistory.size() >= mMaxHistorySize) {
					mHistory.poll();
				}
				mHistory.add(mCurrents.clone());
			}
		}
	};

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			dismissDialog(DIALOG_SAVE_PROGRESS);

			Bundle data = msg.getData();
			if (data.getBoolean("success")) {
				// RAW履歴を初期化
				mRawHistory = new ConcurrentLinkedQueue<float[]>();
			}

			Toast.makeText(MainActivity.this, data.getString("msg"),
					Toast.LENGTH_SHORT).show();

			startGraph();
		}
	};

	private void startGraph() {
		// センサーリスナーを登録
		if (mAccelerometer != null) {
			mSensorManager.registerListener(mSensorEventListener,
					mAccelerometer, mSensorDelay);
		}

		if (!mDrawRoop) {
			// グラフの描画を再開
			mDrawRoop = true;
			mGraphView.surfaceCreated(mGraphView.getHolder());
		}
	}
	
	private void stopGraph() {
		// センサーリスナーを解除
		mSensorManager.unregisterListener(mSensorEventListener);

		// グラフの描画を止める
		mDrawRoop = false;
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "MainActivity.onCreate()");

		Window window = getWindow();

		// 画面をキープ(スリープさせない)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// タイトルを非表示
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		// レイアウトをセット
		setContentView(R.layout.main);

		// フレームレイアウトを取得
		FrameLayout frame = (FrameLayout) findViewById(R.id.frame);

		// リソースを取得
		Resources resources = getResources();

		// 色リソースを取得
		mStringColor = resources.getColor(R.color.string);
		mBGColor = resources.getColor(R.color.background);
		mZeroLineColor = resources.getColor(R.color.zero_line);
		mAngleColors[SensorManager.DATA_X] = resources
				.getColor(R.color.accele_x);
		mAngleColors[SensorManager.DATA_Y] = resources
				.getColor(R.color.accele_y);
		mAngleColors[SensorManager.DATA_Z] = resources
				.getColor(R.color.accele_z);
		mAngleColors[DATA_R] = resources.getColor(R.color.accele_r);

		// グラフビューをフレームレイアウトに追加
		mGraphView = new GraphView(this);
		frame.addView(mGraphView, 0);

		// チェックボックスにリスナーをセット
		CheckBox[] checkboxes = new CheckBox[4];
		checkboxes[SensorManager.DATA_X] = (CheckBox) findViewById(R.id.accele_x);
		checkboxes[SensorManager.DATA_Y] = (CheckBox) findViewById(R.id.accele_y);
		checkboxes[SensorManager.DATA_Z] = (CheckBox) findViewById(R.id.accele_z);
		checkboxes[DATA_R] = (CheckBox) findViewById(R.id.accele_r);
		for (int i = 0; i < 4; i++) {
			if (mGraphs[i]) {
				checkboxes[i].setChecked(true);
			}
			checkboxes[i]
					.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(CompoundButton buttonView,
								boolean isChecked) {
							switch (buttonView.getId()) {
							case R.id.accele_x:
								mGraphs[SensorManager.DATA_X] = isChecked;
								break;
							case R.id.accele_y:
								mGraphs[SensorManager.DATA_Y] = isChecked;
								break;
							case R.id.accele_z:
								mGraphs[SensorManager.DATA_Z] = isChecked;
								break;
							case R.id.accele_r:
								mGraphs[DATA_R] = isChecked;
								break;
							}
						}
					});
		}

		// 値を格納するTextViewを取得
		mAccValueViews[SensorManager.DATA_X] = (TextView) findViewById(R.id.accele_x_value);
		mAccValueViews[SensorManager.DATA_Y] = (TextView) findViewById(R.id.accele_y_value);
		mAccValueViews[SensorManager.DATA_Z] = (TextView) findViewById(R.id.accele_z_value);
		mAccValueViews[DATA_R] = (TextView) findViewById(R.id.accele_r_value);

		// Pass filter 選択ラジオボタンにリスナーを登録
		RadioGroup passFilterGroup = (RadioGroup) findViewById(R.id.pass_filter);
		passFilterGroup.check(R.id.pass_filter_raw);
		passFilterGroup
				.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(RadioGroup group, int checkedId) {
						switch (checkedId) {
						case R.id.pass_filter_raw:
							mPassFilter = PASS_FILTER_RAW;
							break;
						case R.id.pass_filter_low:
							mPassFilter = PASS_FILTER_LOW;
							break;
						case R.id.pass_filter_high:
							mPassFilter = PASS_FILTER_HIGH;
							break;
						}
					}
				});

		// Filter rate 表示用TextViewを取得
		mFilterRateView = (TextView) findViewById(R.id.filter_rate_value);
		mFilterRateView.setText(String.valueOf(mFilterRate));

		// Filter rate 変更シークバーにリスナーを登録
		SeekBar filterRateBar = (SeekBar) findViewById(R.id.filter_rate);
		filterRateBar.setMax(100);
		filterRateBar.setProgress((int) (mFilterRate * 100));
		filterRateBar
				.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						mFilterRate = (float) progress / 100;
						mFilterRateView.setText(String.valueOf(mFilterRate));
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
					}
				});
	}

	@Override
	protected void onStart() {
		Log.i(TAG, "MainActivity.onStart()");

		// 初期化
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		List<Sensor> sensors = mSensorManager
				.getSensorList(Sensor.TYPE_ACCELEROMETER);
		if (sensors.size() > 0) {
			mAccelerometer = sensors.get(0);
		} else {
			Log.e(TAG, "加速度センサーが見つかりませんでした");
		}

		super.onStart();
	}

	@Override
	protected void onResume() {
		Log.i(TAG, "MainActivity.onResume()");

		startGraph();

		super.onResume();
	}

	@Override
	protected void onPause() {
		Log.i(TAG, "MainActivity.onPause()");

		stopGraph();

		super.onPause();
	}

	@Override
	protected void onStop() {
		Log.i(TAG, "MainActivity.onStop()");

		mSensorManager = null;
		mAccelerometer = null;

		super.onStop();
	}

	@Override
	protected void onDestroy() {
		Log.i(TAG, "MainActivity.onDestroy()");

		// TODO 自分のプロセスをキル

		super.onDestroy();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	final public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		switch (event.getAction()) {
		case KeyEvent.ACTION_DOWN:
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				// グラフ倍率をインクリメント
				mGraphScale++;
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (mGraphScale > 1) {
					// グラフ倍率をデクリメント
					mGraphScale--;
				}
				return true;
			case KeyEvent.KEYCODE_FOCUS:
				if (mStatus == STATUS_START) {
					// センサーリスナーを停止
					mSensorManager.unregisterListener(mSensorEventListener);
					mStatus = STATUS_STOP;
				} else {
					if (mAccelerometer != null) {
						// センサーリスナーを再開
						mSensorManager.registerListener(mSensorEventListener,
								mAccelerometer, mSensorDelay);
					}
					mStatus = STATUS_START;
				}
				return true;
			case KeyEvent.KEYCODE_CAMERA:
				return true;
			}
		case KeyEvent.ACTION_UP:
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_VOLUME_UP:
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_FOCUS:
			case KeyEvent.KEYCODE_CAMERA:
				// とりあえず無効
				return true;
			}
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// グラフをY軸方向にスライドできるようにする
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			mTouchOffset = event.getY();
			break;
		case MotionEvent.ACTION_UP:
			mZeroLineY += mZeroLineYOffset;
			mZeroLineYOffset = 0;
			break;
		case MotionEvent.ACTION_MOVE:
			mZeroLineYOffset = (int) (event.getY() - mTouchOffset);
			break;
		}
		return super.onTouchEvent(event);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_SENSOR_DELAY, Menu.NONE,
				R.string.sensor_delay_label).setIcon(
				android.R.drawable.ic_menu_rotate);
		menu.add(Menu.NONE, MENU_START_SAVE, Menu.NONE,
				R.string.start_save_label).setIcon(
				android.R.drawable.ic_menu_recent_history);
		menu.add(Menu.NONE, MENU_SAVE, Menu.NONE, R.string.save_label).setIcon(
				android.R.drawable.ic_menu_save);
		menu.add(Menu.NONE, MENU_END, Menu.NONE, R.string.end_label).setIcon(
				android.R.drawable.ic_menu_close_clear_cancel);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (mRecording) {
			menu.findItem(MENU_START_SAVE).setVisible(false);
			menu.findItem(MENU_SAVE).setVisible(true);
		} else {
			menu.findItem(MENU_START_SAVE).setVisible(true);
			menu.findItem(MENU_SAVE).setVisible(false);
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_SENSOR_DELAY:
			selectSensorDelay();
			break;
		case MENU_START_SAVE:
			mRecording = true;
			Toast.makeText(this, R.string.start_save_msg, Toast.LENGTH_SHORT).show();
			break;
		case MENU_SAVE:
			saveHistory();
			break;
		case MENU_END:
			finish();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void selectSensorDelay() {
		final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
		final CharSequence[] delays = { "FASTEST", "GAME", "UI", "NORMAL" };
		dialogBuilder.setItems(delays, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case 0:
					mSensorDelay = SensorManager.SENSOR_DELAY_FASTEST;
					break;
				case 1:
					mSensorDelay = SensorManager.SENSOR_DELAY_GAME;
					break;
				case 2:
					mSensorDelay = SensorManager.SENSOR_DELAY_UI;
					break;
				case 3:
					mSensorDelay = SensorManager.SENSOR_DELAY_NORMAL;
					break;
				}
				// リスナーを再登録
				stopGraph();
				startGraph();
			}
		});
		dialogBuilder.show();
	}

	private void saveHistory() {
		// レコーディングを停止
		mRecording = false;

		// グラフ停止
		stopGraph();

		// プログレスダイアログを表示
		showDialog(DIALOG_SAVE_PROGRESS);

		// 保存用Threadを開始
		SaveThread thread = new SaveThread();
		thread.start();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_SAVE_PROGRESS:
			ProgressDialog saveProgress = new ProgressDialog(this);
			saveProgress.setTitle("保存中");
			saveProgress.setMessage("履歴を保存しています");
			saveProgress.setIndeterminate(false);
			saveProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			saveProgress.setMax(100);
			saveProgress.setCancelable(false);
			saveProgress.show();
			return saveProgress;
		}
		return super.onCreateDialog(id);
	}

	private class GraphView extends SurfaceView implements
			SurfaceHolder.Callback, Runnable {

		private Thread mThread;
		private SurfaceHolder mHolder;

		public GraphView(Context context) {
			super(context);

			Log.i(TAG, "GraphView.GraphView()");

			mHolder = getHolder();
			mHolder.addCallback(this);

			setFocusable(true);
			requestFocus();
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			Log.i(TAG, "GraphView.surfaceChanged()");
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			Log.i(TAG, "GraphView.surfaceCreated()");

			mDrawRoop = true;
			mThread = new Thread(this);
			mThread.start();
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			Log.i(TAG, "GraphView.surfaceDestroyed()");

			mDrawRoop = false;
			boolean roop = true;
			while (roop) {
				try {
					mThread.join();
					roop = false;
				} catch (InterruptedException e) {
					Log.e(TAG, e.getMessage());
				}
			}
			mThread = null;
		}

		@Override
		public void run() {
			Log.i(TAG, "GraphView.run()");

			int width = getWidth();
			mMaxHistorySize = (int) ((width - 20) / mLineWidth);

			Paint textPaint = new Paint();
			textPaint.setColor(mStringColor);
			textPaint.setAntiAlias(true);
			textPaint.setTextSize(14);

			Paint zeroLinePaint = new Paint();
			zeroLinePaint.setColor(mZeroLineColor);
			zeroLinePaint.setAntiAlias(true);

			Paint[] linePaints = new Paint[4];
			for (int i = 0; i < 4; i++) {
				linePaints[i] = new Paint();
				linePaints[i].setColor(mAngleColors[i]);
				linePaints[i].setAntiAlias(true);
				linePaints[i].setStrokeWidth(2);
			}

			while (mDrawRoop) {
				Canvas canvas = mHolder.lockCanvas();

				if (canvas == null) {
					break;
				}

				canvas.drawColor(mBGColor);

				float zeroLineY = mZeroLineY + mZeroLineYOffset;

				synchronized (mHolder) {
					float twoLineY = zeroLineY - (20 * mGraphScale);
					float oneLineY = zeroLineY - (10 * mGraphScale);
					float minasOneLineY = zeroLineY + (10 * mGraphScale);
					float minasTwoLineY = zeroLineY + (20 * mGraphScale);

					canvas.drawText("2", 5, twoLineY + 5, zeroLinePaint);
					canvas.drawLine(20, twoLineY, width, twoLineY,
							zeroLinePaint);

					canvas.drawText("1", 5, oneLineY + 5, zeroLinePaint);
					canvas.drawLine(20, oneLineY, width, oneLineY,
							zeroLinePaint);

					canvas.drawText("0", 5, zeroLineY + 5, zeroLinePaint);
					canvas.drawLine(20, zeroLineY, width, zeroLineY,
							zeroLinePaint);

					canvas.drawText("-1", 5, minasOneLineY + 5, zeroLinePaint);
					canvas.drawLine(20, minasOneLineY, width, minasOneLineY,
							zeroLinePaint);

					canvas.drawText("-2", 5, minasTwoLineY + 5, zeroLinePaint);
					canvas.drawLine(20, minasTwoLineY, width, minasTwoLineY,
							zeroLinePaint);

					if (mHistory.size() > 1) {
						Iterator<float[]> iterator = mHistory.iterator();
						float[] before = new float[4];
						int x = width - mHistory.size() * mLineWidth;
						int beforeX = x;
						x += mLineWidth;

						if (iterator.hasNext()) {
							float[] history = iterator.next();
							for (int angle = 0; angle < 4; angle++) {
								before[angle] = zeroLineY
										- (history[angle] * mGraphScale);
							}
							while (iterator.hasNext()) {
								history = iterator.next();
								for (int angle = 0; angle < 4; angle++) {
									float startY = zeroLineY
											- (history[angle] * mGraphScale);
									float stopY = before[angle];
									if (mGraphs[angle]) {
										canvas.drawLine(x, startY, beforeX,
												stopY, linePaints[angle]);
									}
									before[angle] = startY;
								}
								beforeX = x;
								x += mLineWidth;
							}
						}
					}
				}

				mHolder.unlockCanvasAndPost(canvas);

				try {
					Thread.sleep(mDrawDelay);
				} catch (InterruptedException e) {
					Log.e(TAG, e.getMessage());
				}
			}
		}
	}

	private class SaveThread extends Thread {
		@Override
		public void run() {
			// CSVデータを作成
			StringBuilder csvData = new StringBuilder();
			Iterator<float[]> iterator = mRawHistory.iterator();
			while (iterator.hasNext()) {
				float[] values = iterator.next();
				for (int angle = 0; angle < 3; angle++) {
					csvData.append(String.valueOf(values[angle]));
					if (angle < 3) {
						csvData.append(",");
					}
				}
				csvData.append("\n");
			}

			// メッセージングの準備
			Message msg = new Message();
			Bundle bundle = new Bundle();

			try {
				// SDカードにディレクトリがなければ作成
				String appName = getResources().getString(R.string.app_name);
				String dirPath = Environment.getExternalStorageDirectory()
						.toString()
						+ "/" + appName;
				File dir = new File(dirPath);
				if (!dir.exists()) {
					dir.mkdirs();
				}

				// CSVファイルへ保存
				String fileName = DateFormat.format("yyyy-MM-dd-kk-mm-ss",
						System.currentTimeMillis()).toString().concat(".csv");
				File file = new File(dirPath, fileName);
				if (file.createNewFile()) {
					FileOutputStream fileOutputStream = new FileOutputStream(
							file);
					// データを書き込む
					fileOutputStream.write(csvData.toString().getBytes());
					fileOutputStream.close();
				}

				// ファイルへの出力完了を通知
				bundle.putString("msg", MainActivity.this.getResources()
						.getString(R.string.save_complate));
				bundle.putBoolean("success", true);
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());

				// ファイルへの出力失敗を通知
				bundle.putString("msg", e.getMessage());
				bundle.putBoolean("success", false);
			}

			// ハンドラを使ってメッセージング
			msg.setData(bundle);
			mHandler.sendMessage(msg);
		}
	}
}