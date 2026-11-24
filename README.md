<img width="5120" height="2880" alt="さんとぴ1" src="https://github.com/user-attachments/assets/67b8058b-421d-4930-91f0-8e2f94a37274" />
<img width="5120" height="2880" alt="さんとぴ2" src="https://github.com/user-attachments/assets/a41dfd42-e9e3-4525-99c2-6fadba3e3ddd" />
<img width="5120" height="2880" alt="さんとぴ3" src="https://github.com/user-attachments/assets/391bdf7f-ab17-41fe-a722-5a0d9383c2aa" />
<img width="5120" height="2880" alt="さんとぴ4" src="https://github.com/user-attachments/assets/e1b3c2a3-ebfa-4742-bec6-1725dd120c30" />
<img width="5120" height="2880" alt="さんとぴ5" src="https://github.com/user-attachments/assets/0ed06ec5-8111-4841-aad7-4a8451634b87" />

#概要
さんとぴメディアコントロールは、デバイスで再生中のメディアをコントロールしたり、歌詞をみたり、聴いた時間を記録しレポートにできるアプリです。
カスタマイズ性にすぐれており、多数の設定項目があり、ご自身の好きなようにカスタムすることができます。
歌詞画面は特に力を入れており、一部の曲(lrc.redで取得したもののみ)や自分で作ったファイル(ttmlのみ)を読み込むことで、1文字ごとのなめらかな歌詞を表示できます。また、3か月以上試行錯誤して開発した洗練されたアニメーションと、歌詞をみやすいように自動調整する「歌詞の最適化」や、アーティストごとに歌詞の表示方法を変える「アーティストリリックプリセット」など、オリジナル機能を多数備えています。

アプリは現在開発中です。現時点での完成度でお試しできます。バグがある点にご留意ください。

# アプリ使用時の注意
このアプリは今再生しているメディアをソースに動作します。通知の管理権限の許可が必要です。
権限を付与しようとしたら「制限付き設定」が出る場合があります。その場合、もう一度このアプリの「アプリ情報」にアクセスし、右上3点マークから「制限付き設定を許可」をタップして許可すると権限を付与できます。

歌詞の取得はLRCLIBと、LRC.Redの音節ごとの歌詞・行ごとの歌詞,Sdlrc、LRC、ttml、lrc.redからダウンロードしたyamlの読み込みに対応しています。
Sdlrcの作成は、**「Sdlrcメーカー」アプリ**より作成できます。
https://github.com/sandolpin/sdlrcmaker2

**メディアコントロールのないAndroid15以前のZTE端末などでは動作しません。**
**このアプリはAndroid12以降でほぼすべての機能が使えます。Android11以前では歌詞・背景のぼかしなどに制限があります。**
