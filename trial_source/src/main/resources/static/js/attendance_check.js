// 飯塚麻美子 - Task.27

/**
 * 更新確認
 * @author 飯塚麻美子 - Task.27
 */
document.querySelector('input[name="complete"]').addEventListener('click', function(e) {
	
	// 更新確認ダイアログを出す
	if (!confirm("更新します。よろしいですか？")) {
		//キャンセル時に送信を止める
		e.preventDefault(); 
		return;
	}
});