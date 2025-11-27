package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());

		// 飯塚麻美子 - Task.26
		attendanceForm.setHour(attendanceUtil.getHourMap());
		attendanceForm.setMinute(attendanceUtil.getMinuteMap());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));

			// 飯塚麻美子 - Task.26
			if (attendanceManagementDto.getTrainingStartTime() != null)
				dailyAttendanceForm.setTrainingStartHour(
						attendanceUtil.getHour(attendanceManagementDto.getTrainingStartTime()));
			if (attendanceManagementDto.getTrainingStartTime() != null)
				dailyAttendanceForm.setTrainingStartMinute(
						attendanceUtil.getMinute(attendanceManagementDto.getTrainingStartTime()));
			if (attendanceManagementDto.getTrainingEndTime() != null)
				dailyAttendanceForm.setTrainingEndHour(
						attendanceUtil.getHour(attendanceManagementDto.getTrainingEndTime()));
			if (attendanceManagementDto.getTrainingEndTime() != null) {
				dailyAttendanceForm.setTrainingEndMinute(
						attendanceUtil.getMinute(attendanceManagementDto.getTrainingEndTime()));
			}

			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException、IllegalArgumentException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {
		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 飯塚麻美子 -Task.27
		// エラーチェック用のStringリストとエラーメッセージ一覧
		List<String> check = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		// 「備考」と「100」の文字列、「出勤時間」「退勤時間」の文字列
		String[] maxLength = new String[] { "備考", "100" };
		String[] startTime = new String[] { "出勤時間" };
		String[] endTime = new String[] { "退勤時間" };
		// エラーチェック用のカウント
		Integer count = 0;

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 飯塚麻美子 - Task.27
			count++;
			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());

			// 飯塚麻美子 - Task.26
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			try {
				trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartHour(),
						dailyAttendanceForm.getTrainingStartMinute());
				tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			} catch (IllegalArgumentException e) {

				// 飯塚麻美子 -Task.27
				// b：出勤時間(時)(分)の一方が入力あり、もう一方が入力なし
				if (!(dailyAttendanceForm.getTrainingStartHour() == null
						&& dailyAttendanceForm.getTrainingStartMinute() == null)
						&& !check.stream().anyMatch(s -> s.contains("b"))) {
					if(dailyAttendanceForm.getTrainingStartHour() == null)
						check.add("bh" + (count-1));
					if(dailyAttendanceForm.getTrainingStartMinute() == null)
						check.add("bm" + (count-1));
					String errorMessage = messageUtil.getMessage(Constants.INPUT_INVALID, startTime);
					errors.add(errorMessage);
				}
				// 両方未入力の場合を考え空欄をセット
				tStudentAttendance.setTrainingStartTime("");
			}

			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			try {
				trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndHour(),
						dailyAttendanceForm.getTrainingEndMinute());
				tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			} catch (IllegalArgumentException e) {

				// 飯塚麻美子 -Task.27
				// c：退勤時間(時)(分)の一方が入力あり、もう一方が入力なし
				if (!(dailyAttendanceForm.getTrainingEndHour() == null
						&& dailyAttendanceForm.getTrainingEndMinute() == null)
						&& !check.stream().anyMatch(s -> s.contains("c"))) {
					if(dailyAttendanceForm.getTrainingEndHour() == null)
						check.add("ch" + (count-1));
					if(dailyAttendanceForm.getTrainingEndMinute() == null)
						check.add("cm" + (count-1));
					String errorMessage = messageUtil.getMessage(Constants.INPUT_INVALID, endTime);
					errors.add(errorMessage);
				}
				// 両方未入力の場合を考え空欄をセット
				tStudentAttendance.setTrainingEndTime("");

			}

			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());

			// 飯塚麻美子 -Task.27
			// a：備考の文字数 ＞ 100
			if (dailyAttendanceForm.getNote().length() > 100 && !check.stream().anyMatch(s -> s.contains("f"))) {
				check.add("a" + (count - 1));
				String errorMessage = messageUtil.getMessage(Constants.VALID_KEY_MAXLENGTH, maxLength);
				errors.add(errorMessage);
			}
			// d：出勤時間に入力なし ＆ 退勤時間に入力あり
			if (trainingStartTime == null && trainingEndTime != null) {
				check.add("d" + (count - 1));
				String errorMessage = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
				errors.add(errorMessage);
			} else if (trainingStartTime != null && trainingEndTime != null) {
				try {

					// 勤務時間(出勤時間～退勤時間までの時間)
					TrainingTime jukoTime = attendanceUtil.calcJukoTime(trainingStartTime, trainingEndTime);
					Integer total = 0;
					total = total + attendanceUtil.getHour(jukoTime.getFormattedString()) * 60;
					total = total + attendanceUtil.getMinute(jukoTime.getFormattedString());
					// 中抜け時間
					Integer blank = 0;
					if (dailyAttendanceForm.getBlankTime()!= null) {
						blank = dailyAttendanceForm.getBlankTime();
					}
					System.out.println("blank = " + blank);
					System.out.println("total = " + total);
					// f：中抜け時間 ＞ 勤務時間(出勤時間～退勤時間までの時間)の場合
					if (blank > total && !check.stream().anyMatch(s -> s.contains("f"))) {
						check.add("f" + (count - 1));
						String errorMessage = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_BLANKTIMEERROR);
						errors.add(errorMessage);
					}
					// e：退勤時間 ＞ 出勤時間
				} catch (UnsupportedOperationException e) {
					check.add("e" + (count - 1));
					String[] countString = new String[] { Integer.toString(count) };
					String errorMessage = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE,
							countString);
					errors.add(errorMessage);
					// 出勤・退勤ともに空欄の場合 → 特に何もしなくていい。
				} catch (NullPointerException e) {
				}
			}
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		if (!errors.isEmpty()) {
			// エラーメッセージの整頓
			List<Map.Entry<String, String>> errorSort = new ArrayList<>();
			for (int i = 0; i < check.size(); i++) {
				errorSort.add(Map.entry(check.get(i), errors.get(i)));
			}
			// 先頭1文字を基にソート
			errorSort.sort(Comparator.comparing(e -> e.getKey().substring(0, 1)));
			// 文字列化するためのList
			List<String> errorList = new ArrayList<>();
			for (Map.Entry<String, String> error : errorSort) {
				// エラーコード + 配列番号
				errorList.add(error.getKey() + "_" + error.getValue());
			}
			String errorMessages = String.join(";", errorList);
			throw new IllegalArgumentException(errorMessages);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 過去日の勤怠不備確認
	 * 
	 * @author 飯塚麻美子 - Task.25
	 * @param lmsUserId
	 * @return 過去勤怠不備の有無(有：true、無：false)
	 */
	public boolean getPastAttendanceManagement(Integer lmsUserId) {
		// 今日の日付取得
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 過去勤怠の未入力数をカウント
		Integer checkPastError = tStudentAttendanceMapper.notEnterCount(
				lmsUserId, Constants.DB_FLG_FALSE, trainingDate);
		// 識別用boolean
		boolean hasPastError;
		// 取得した未入力カウント数が0より大きい場合true
		if (checkPastError > 0) {
			hasPastError = true;
			// それ以外はfalse
		} else {
			hasPastError = false;
		}
		// 過去勤怠の未入力数をカウントする
		return hasPastError;
	}

	/**
	 * プルダウンリストをFormにセット・選択して返す
	 * 
	 */
	public AttendanceForm setPulldownList(AttendanceForm attendanceForm) {

		// 飯塚麻美子 - Task.27
		attendanceForm.setHour(attendanceUtil.getHourMap());
		attendanceForm.setMinute(attendanceUtil.getMinuteMap());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
		for(DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {
			if(dailyAttendanceForm.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTimeValue(attendanceUtil.convertBlankTime(dailyAttendanceForm.getBlankTime()));;
			}else {
				dailyAttendanceForm.setBlankTime(null);
			}
		}		
		return attendanceForm;
	}

	/**
	 * エラーメッセージを連結した文字列をMapにして返す
	 * 
	 * @author 飯塚麻美子 - Task.27
	 * @param errorMessage
	 * @return エラーメッセージマップ
	 */
	public Map<String, String> changeErrorMessageList(String errorMessage) {
		Map<String, String> errors = new HashMap<>();
		String[] messages = errorMessage.split(";");
		for(String message : messages) {
			String[] keyValue = message.split("_");
			// key + message ?
			if(keyValue.length == 2) {
				errors.put(keyValue[0], keyValue[1]);
			}
		}
		return errors;
	}
}
