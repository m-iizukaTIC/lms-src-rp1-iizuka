package jp.co.sss.lms.util;

import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.mapper.MSectionMapper;

/**
 * 勤怠管理のユーティリティクラス
 * 
 * @author 東京ITスクール
 */
@Component
public class AttendanceUtil {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private MSectionMapper mSectionMapper;

	/**
	 * SSS定時・出退勤時間を元に、遅刻早退を判定をする
	 * 
	 * @param trainingStartTime 開始時刻
	 * @param trainingEndTime   終了時刻
	 * @return 遅刻早退を判定メソッド
	 */
	public AttendanceStatusEnum getStatus(TrainingTime trainingStartTime,
			TrainingTime trainingEndTime) {
		return getStatus(trainingStartTime, trainingEndTime, Constants.SSS_WORK_START_TIME,
				Constants.SSS_WORK_END_TIME);
	}

	/**
	 * 与えられた定時・出退勤時間を元に、遅刻早退を判定する
	 * 
	 * @param trainingStartTime 開始時刻
	 * @param trainingEndTime   終了時刻
	 * @param workStartTime     定時開始時刻
	 * @param workEndTime       定時終了時刻
	 * @return 判定結果
	 */
	private AttendanceStatusEnum getStatus(TrainingTime trainingStartTime,
			TrainingTime trainingEndTime, TrainingTime workStartTime, TrainingTime workEndTime) {
		// 定時が不明な場合、NONEを返却する
		if (workStartTime == null || workStartTime.isBlank() || workEndTime == null
				|| workEndTime.isBlank()) {
			return AttendanceStatusEnum.NONE;
		}
		boolean isLate = false, isEarly = false;
		// 定時より1分以上遅く出社していたら遅刻(＝はセーフ)
		if (trainingStartTime != null && trainingStartTime.isNotBlank()) {
			isLate = (trainingStartTime.compareTo(workStartTime) > 0);
		}
		// 定時より1分以上早く退社していたら早退(＝はセーフ)
		if (trainingEndTime != null && trainingEndTime.isNotBlank()) {
			isEarly = (trainingEndTime.compareTo(workEndTime) < 0);
		}
		if (isLate && isEarly) {
			return AttendanceStatusEnum.TARDY_AND_LEAVING_EARLY;
		}
		if (isLate) {
			return AttendanceStatusEnum.TARDY;
		}
		if (isEarly) {
			return AttendanceStatusEnum.LEAVING_EARLY;
		}
		return AttendanceStatusEnum.NONE;
	}

	/**
	 * 中抜け時間を時(hour)と分(minute)に変換
	 *
	 * @param min 中抜け時間
	 * @return 時(hour)と分(minute)に変換したクラス
	 */
	public TrainingTime calcBlankTime(int min) {
		int hour = min / 60;
		int minute = min % 60;
		TrainingTime total = new TrainingTime(hour, minute);
		return total;
	}

	/**
	 * 時刻分を丸めた本日日付を取得
	 * 
	 * @return "yyyy/M/d"形式の日付
	 */
	public Date getTrainingDate() {
		Date trainingDate;
		try {
			trainingDate = dateUtil.parse(dateUtil.toString(new Date()));
		} catch (ParseException e) {
			// DateUtil#toStringとparseは同様のフォーマットを使用しているため、起こりえないエラー
			throw new IllegalStateException();
		}
		return trainingDate;
	}

	/**
	 * 休憩時間取得
	 * 
	 * @return 休憩時間
	 */
	public LinkedHashMap<Integer, String> setBlankTime() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
		map.put(null, "");
		for (int i = 15; i < 480;) {
			int hour = i / 60;
			int minute = i % 60;
			String time;

			if (hour == 0) {
				time = minute + "分";

			} else if (minute == 0) {
				time = hour + "時間";
			} else {
				time = hour + "時" + minute + "分";
			}

			map.put(i, time);

			i = i + 15;

		}
		return map;
	}

	/**
	 * 研修日の判定
	 * 
	 * @param courseId
	 * @param trainingDate
	 * @return 判定結果
	 */
	public boolean isWorkDay(Integer courseId, Date trainingDate) {
		Integer count = mSectionMapper.getSectionCountByCourseId(courseId, trainingDate);
		if (count > 0) {
			return true;
		}
		return false;
	}

	/**
	 * 時間のプルダウンマップを生成
	 * 
	 * @author 飯塚麻美子 - Task.26
	 * @return 1時間刻みの時間(数値)マップ
	 */
	public LinkedHashMap<Integer, String> getHourMap() {
		LinkedHashMap<Integer, String> hours = new LinkedHashMap<>();
		for (int i = 0; i < 24; i++) {
			hours.put(i, String.format("%02d", i));
		}
		return hours;
	}

	/**
	 * 分のプルダウンマップを生成
	 * 
	 * @author 飯塚麻美子 - Task.26
	 * @return 1分刻みの分(数値)マップ
	 */
	public LinkedHashMap<Integer, String> getMinuteMap() {
		LinkedHashMap<Integer, String> minutes = new LinkedHashMap<>();
		for (int i = 0; i < 60; i++) {
			minutes.put(i, String.format("%02d", i));
		}
		return minutes;
	}

	/**
	 * 時間(時)の切り出し
	 * 
	 * @author 飯塚麻美子 - Task.26
	 * @param trainingTime
	 * @return 出退勤時間(時間)
	 */
	public Integer getHour(String trainingTime) {
		Integer hour = null;
		try {
			hour = Integer.parseInt(trainingTime.substring(0, 2));
			return hour;
		} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
			return null;
		}
	}

	/**
	 * 時間(分)の切り出し
	 * 
	 * @author 飯塚麻美子 - Task.26
	 * @param trainingTime
	 * @return 出退勤時間(分)
	 */
	public Integer getMinute(String trainingTime) {
		Integer minute = null;
		try {
			minute = Integer.parseInt(trainingTime.substring(trainingTime.length() - 2));
			return minute;
		} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
			return null;
		}
	}

	/**
	 * 受講時間数を算出
	 * 
	 * @author 飯塚麻美子 - Task.27
	 * @param startTime
	 * @param endTime
	 * @return 受講トータル時間
	 */
	public TrainingTime calcJukoTime(TrainingTime startTime, TrainingTime endTime) {
		TrainingTime jukoTime = endTime;
		try {
			jukoTime.subtract(startTime);
		} catch (UnsupportedOperationException e) {
			throw new UnsupportedOperationException("未実装");
		}
		return jukoTime;
	}

	/**
	 * 中抜け時間(文字列)を数字に変換
	 * 
	 * @author 飯塚麻美子 - Task.27
	 * @param blankTime
	 * @return 中抜け時間
	 */
	public Integer reverseBlankTime(String blankTime) {
		Integer blank = 0;
		if (blankTime.indexOf("時間") != -1) {
			String blankHourString = blankTime.substring(0, blankTime.indexOf("時間"));
			Integer blankHour = Integer.parseInt(blankHourString);
			blank = blank + blankHour * 60;
		}
		if (blankTime.indexOf("分") != -1) {
			Integer minuteStart = 0;
			if (blankTime.indexOf("時間") != -1) {
				minuteStart = blankTime.indexOf("時間") + 2;
			} else {
				minuteStart = 0;
			}
			String minuteString = blankTime.substring(minuteStart, blankTime.indexOf("分"));
			blank = blank + Integer.parseInt(minuteString);
		}
		return blank;
	}

	/**
	 * プルダウン初期表示用の中抜け時間を返却
	 * 
	 * @author 飯塚麻美子 - Task.27
	 * @param blankTime
	 * @return 中抜け時間（〇〇時〇〇分の文字列）
	 */
	public String convertBlankTime(Integer blankTime) {
		LinkedHashMap<Integer, String> blankTimeList = setBlankTime();
		String blank = blankTimeList.get(blankTime);
		return blank;
	}
}