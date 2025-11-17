/**
 * 過去日の未入力チェック
 */

document.querySelector('input[name="complete"]').addEventListener('click', function(e) {

	// <tbody>内のすべての<tr>をリストで取得
	let rows = document.querySelectorAll("tbody tr");

	// 各行の内容確認_名前が「〇〇」で終わるもの
	for (let row of rows) {
		// 「研修日」
		let date = row.querySelector("input[name$='.trainingDate']").value;
		// 「出勤時間」
		let start = row.querySelector("input[name$='.trainingStartTime']").value;
		// 「退勤時間」
		let end = row.querySelector("input[name$='.trainingEndTime']").value;
		// Date型にフォーマット
		let trainingDate = new Date(date);
		// 今日の日付回収
		let today = new Date();
		today.setHours(0, 0, 0, 0);

		// 過去日に未入力があるなら、エラーダイアログを出す
		if (trainingDate < today && (start === "" || end === "")) {
			alert("過去日の勤怠に未入力があります。");
			e.preventDefault();
			return false;
		}
	}
	// 何も問題なかったら更新確認ダイアログを出す
	if (!confirm("更新します。よろしいですか？")) {
		//キャンセル時に送信を止める
		e.preventDefault(); 
		return;
	}
});