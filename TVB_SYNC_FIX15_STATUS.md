# ALAD-Mobile FIX15 — Lồng tiếng TVB khớp với video phát gốc

> Sửa lại 2026-11-09 17:24 theo ý user: **không đổi chất giọng đọc thành
> giọng TVB chung chung**. Giọng đọc giữ nguyên tự nhiên, không còn chỉnh
> pitch/rate theo nam/nữ (phần đó của FIX12/FIX14 đã bỏ trong bản này).
> Cái "TVB" ở đây chỉ là cách lồng tiếng: câu dịch khớp đúng thời lượng
> và thời điểm của câu gốc trên hình, như cách phim TVB lồng tiếng vẫn làm.
>
> Sửa lại 2026-11-09 17:32 theo video mẫu user gửi
> (https://youtu.be/VLvRGuHCpGA — HTV2 phỏng vấn đội lồng tiếng Sài Gòn
> Phim: "Sài Gòn phim dịch và lồng tiếng, FaFilm Việt Nam phát hành..."):
> giọng dịch làm theo **kiểu lồng tiếng cổ điển** trong video đó — ấm,
> truyền cảm, rõ ràng, vừa phải, một nhân vật giữ một giọng suốt phim.
> Đây là phong cách chung làm bằng pitch/rate của TTS, **không phải
> clone giọng của bất kỳ diễn viên lồng tiếng thật nào** (không dùng bản
> ghi hay mẫu giọng của ai).

## Vấn đề trước bản này

Bản FIX14 đã có giọng TVB (Android TTS tiếng Việt, đổi giọng nam/nữ theo
nhân vật) nhưng **dịch chưa khớp hình**, vì 3 chỗ đều không có khái niệm
"thời gian của câu gốc":

1. **Độ trễ chỉ đo một lần.** Lúc bắt đầu phiên, app đo thời gian từ mẫu
   âm thanh đầu tiên gửi lên Gemini tới đoạn âm thanh dịch đầu tiên, cộng
   2 giây, rồi giữ nguyên con số đó suốt buổi xem. Mạng nhanh/chậm đi là
   hình và tiếng lệch dần, không tự sửa.
2. **Đường TVB (TTS) không hề chờ hình.** `AudioPlayerManager` có giữ
   giọng dịch lại cho khớp video trễ, nhưng `TvbVoiceDirector` thì nói
   ngay khi chữ của Gemini vừa về — tức là nói trước hình trễ khoảng 5
   giây. Câu tiếng Việt cũng nói ở tốc độ cố định, bất kể câu gốc dài hay
   ngắn, nên câu dài nói lố sang câu sau, câu sau lại càng lố hơn.
3. **Prompt không bắt vừa miệng.** Prompt cũ chỉ nói "ngắn gọn, tự nhiên",
   không hề nói câu dịch phải nói hết trong đúng thời gian câu gốc nói.
   Gemini cứ dịch đủ ý bằng câu dài là miệng nhân vật đã ngậm mà tiếng
   Việt vẫn còn nói.
4. **Phụ đề hiện theo mạng, không theo hình.** Phụ đề dịch append ngay khi
   chữ về, sớm hơn hình trễ vài giây.

## Hướng phát triển: lồng tiếng kiểu TVB

Lồng tiếng TVB không phải chỉ là "dịch đúng nghĩa rồi đọc lên". Chuẩn của
nó là: **câu dịch phải vừa đúng thời lượng câu gốc** — bắt đầu lúc nhân
vật mở miệng, kết thúc lúc nhân vật ngậm miệng, giữ nguyên cảm xúc và
cách xưng hô của nhân vật. Bản này đưa chuẩn đó vào app bằng 4 cơ chế:

### 1. Đo thời gian câu gốc — `DubSyncTracker` (mới)

`app/src/main/kotlin/com/alad/app/core/audio/DubSyncTracker.kt`

- Mọi khối PCM 16 kHz/10 ms gửi lên Gemini đều đi qua tracker. Bộ phát
  hiện giọng nói (RMS > 600) đánh dấu lúc một câu gốc bắt đầu và lúc nó
  kết thúc (im lặng 450 ms thì đóng câu, câu dưới 250 ms coi là nhiễu).
- Khi một câu dịch về, `utteranceForSegment()` trả về câu gốc mà nó thuộc
  về: thời điểm bắt đầu + thời lượng. Nếu nhân vật còn đang nói thì trả về
  câu đang mở, đánh dấu "chưa xong" — lúc đó chỉ dùng được thời điểm bắt
  đầu, không dùng độ dài tạm thời để tính tốc độ đọc. Vì đường WebAudio
  chỉ gửi khối PCM khi có tiếng (lúc im lặng không có khối nào tới), chỗ
  im lặng còn được suy ra từ khoảng trống giữa các khối lúc truy vấn.
- `recordTranslationLatency()` đo từng câu một: từ lúc câu gốc kết thúc
  tới lúc bản dịch về, cộng 2 giây đệm, rồi lấy trung bình trượt (30%
  trọng số cho mẫu mới). Độ trễ hình theo tốc độ thật của Gemini từng lúc,
  không còn là một con số đo một lần đầu phiên.

### 2. Giọng lồng tiếng cổ điển, chờ hình, vừa miệng — `TvbVoiceDirector`

- Mỗi `Segment` thêm `sourceDurationMs`, `speakAtMs` (lúc video trễ tới
  đúng câu này), `deadlineMs` (lúc muộn nhất còn được nói).
- Director **chờ tới `speakAtMs`** mới bắt đầu đọc, thay vì đọc ngay khi
  chữ về.
- **Giọng theo video mẫu:** hồ sơ `ClassicDubStyle` trong
  `TvbVoiceDirector` — nam pitch 0.94 / tốc độ 0.95, nữ pitch 1.04 /
  tốc độ 0.97. Cố tình giữ gần mức tự nhiên: kiểu lồng tiếng cổ điển nằm
  ở sự ấm, rõ và vừa phải, không phải giọng trầm hẳn hay cao hẳn. Một
  nhân vật giữ một giọng suốt phim nhờ `speaker_id` ổn định — đúng cách
  đội Sài Gòn Phim phân công một diễn viên lồng cố định cho một diễn
  viên. Đây là phong cách chung, không phải giọng của diễn viên nào.
- **Tốc độ đọc** tự tính theo độ dài câu gốc: ước lượng câu tiếng Việt
  cần bao lâu (khoảng 85 ms/ký tự ở tốc độ 1.0), rồi tăng tốc độ TTS lên
  tới tối đa 1.30× để vừa vào thời lượng câu gốc. Câu ngắn thì không cần
  tăng.
- Nếu máy có giọng TTS tiếng Việt ấm hơn, chọn nó trong cài đặt TTS của
  hệ thống sẽ cải thiện tiếng hơn bất kỳ con số pitch nào ở đây; app
  không tự thay được giọng TTS hệ thống.
- Câu nào đã quá `deadlineMs` (hình đã qua rồi) thì **bỏ luôn, không xếp
  hàng**. Nói một câu muộn sẽ đẩy mọi câu sau muộn theo — đó là cách độ
  lệch tích lũy cả tập phim trước đây.
- `onSegmentStarted` báo lúc câu thực sự bắt đầu nói; phụ đề giờ hiện
  theo tiếng nói chứ không theo mạng.

### 3. Prompt TVB — `ALADWebSocketManager`

Prompt hệ thống giờ dặn Gemini đúng luật lồng tiếng TVB: câu dịch phải
nói hết trong đúng thời gian câu gốc, câu gốc ngắn thì câu dịch ngắn
(bỏ từ đệm, giữ ý chính), không thêm giải thích hay từ thừa, kết thúc
đúng lúc nhân vật dừng nói, không kéo chữ sang cảnh sau; giữ đúng cảm xúc
và xưng hô tiếng Việt theo quan hệ nhân vật. Prompt cũng hướng dẫn lối
đọc kiểu lồng tiếng cổ điển trong video mẫu: ấm, truyền cảm, rõ ràng,
kịch tính vừa phải, không đều đều, không vội, không gào — và nói rõ đây
là phong cách đọc, **không phải bắt chước giọng diễn viên thật nào**.
Marker nhân vật `[[TVB:id:M/F]]` giữ nguyên như FIX12/FIX14.

### 4. Nối dây + nhìn thấy được độ lệch

- `AudioDubbingForegroundService`: một đường PCM chung (`onSourcePcm`)
  cho cả hai nguồn thu (WebAudio trong trình duyệt và system capture),
  vừa gửi lên Gemini vừa nuôi tracker — trước đây hai đường này viết
  riêng, chỉ cần quên một đường là tracker mù. Lúc `turnComplete` thì
  đóng câu gốc đang mở để câu cuối có đủ độ dài. Độ trễ đo theo từng câu
  cập nhật vào `estimatedDubbingDelayMs` mà `BrowserActivity` vẫn đang
  poll mỗi 500 ms.
- `DubbingSessionRepository`: thêm 4 chỉ số đồng bộ
  (`lastSourceUtteranceMs`, `lastDubLatencyMs`, `lastDubOverrunMs`,
  `droppedDubSegments`).
- Màn hình chính hiện dòng trạng thái TVB dưới thanh "Tốc độ giọng dịch"
  khi đang lồng tiếng, ví dụ: `🎬 TVB khớp hình: câu gốc 2,4s • dịch về
  sau 1,2s • lố 300ms • đã bỏ 1 câu cũ`. Nhãn độ trễ trong trình duyệt
  cũng hiện thêm "đã bỏ N câu cũ" / "câu dịch lố N ms" khi có.

## Những gì KHÔNG đổi

- Marker `[[TVB:id:M/F]]` và hàng đợi TTS nối tiếp giữ nguyên; hồ sơ
  giọng cổ điển (`ClassicDubStyle`) thay cho pitch/rate cũ của FIX12/
  FIX14, gần mức tự nhiên hơn và có ghi rõ nguồn phong cách.
- Đường âm thanh Gemini (PCM) vẫn dùng giọng persona đã chọn trong Cài
  đặt. Muốn gần kiểu lồng tiếng cổ điển nhất trên đường này thì chọn
  persona ấm: nữ/kể chuyện ấm dùng Aoede, nam trầm ấm dùng Charon; phần
  hướng dẫn lối đọc trong prompt áp dụng cho cả hai đường.
- Đường âm thanh Gemini (PCM) và `AudioPlayerManager` giữ nguyên; bản
  này chỉ sửa đường TVB bằng chữ/TTS.
- Đây vẫn là TTS hệ thống Android, không phải clone giọng diễn viên TVB
  nào, không có neural TTS thật (xem ghi chú FIX12/FIX13).

## Giới hạn — nói thẳng

- **Không phải lip-sync từng phoneme.** Gemini Live không trả timestamp
  từng từ của âm thanh gốc, nên không có code nào trong repo này khớp
  được môi từng âm. Bản này khớp ở mức câu: bắt đầu đúng lúc, kết thúc
  đúng lúc, không lấn câu sau.
- Tốc độ 85 ms/ký tự chỉ là ước lượng; TTS từng máy khác nhau. Nếu trên
  máy thật câu vẫn lố đều, chỉnh `MS_PER_CHAR_AT_RATE_1` trong
  `TvbVoiceDirector` cho khớp máy đó.
- Ngưỡng RMS 600 có thể cần chỉnh theo phim: phim nhạc nền lớn có thể
  làm tracker tưởng nhạc là lời thoại; phim thoại nhỏ có thể bị mất câu.
  Nếu câu gốc đo sai thì `speakAtMs`/`sourceDurationMs` sai theo.
- Chỉ áp dụng cho video HTML5 trong trình duyệt ALAD (đường DelayNode +
  canvas). DRM/CORS/surface được bảo vệ vẫn không trễ được hình như cũ.
- Chưa biên dịch APK trên máy này: không có JDK/Android SDK, Gradle
  wrapper cần tải Gradle 9.3.0 (xem ghi chú FIX14). Cần build và kiểm
  chứng trên máy thật.

## Kiểm chứng trên máy thật (vivo)

1. Build: `./gradlew assembleDebug`, cài APK, nhập Gemini API key.
2. Mở một phim TVB (Quảng Đông) trong trình duyệt ALAD, bật lồng tiếng,
   để tiếng Việt, xem 5 phút.
3. Kiểm tra bằng mắt/tai, không đoán:
   - Câu tiếng Việt bắt đầu đúng lúc nhân vật mở miệng, kết thúc lúc
     nhân vật ngậm miệng (so với trước bản này: nói trước hình ~5 giây).
   - Dòng trạng thái TVB ở màn hình chính: "câu gốc" và "dịch về sau"
     cập nhật theo từng câu; "lố" càng nhỏ càng tốt, "đã bỏ câu cũ"
     càng ít càng tốt (0 là tốt nhất).
   - Câu dài của nhân vật không lấn sang câu sau; nếu có lấn, ghi lại
     độ "lố" hiển thị để chỉnh `MS_PER_CHAR_AT_RATE_1` hoặc
     `MAX_SYNC_RATE`.
   - Phụ đề dịch hiện cùng lúc với giọng nói, không hiện trước hình.
   - Giọng nghe ấm, rõ, vừa phải như kiểu lồng tiếng cổ điển trong
     video mẫu, không đều đều như máy đọc; mỗi nhân vật giữ một giọng.
     Nếu nghe vẫn chưa giống, chỉnh 4 con số trong `ClassicDubStyle`
     (`TvbVoiceDirector.kt`) hoặc chọn giọng TTS tiếng Việt ấm hơn trong
     cài đặt TTS của hệ thống.
4. Thử cả hai đường thu: xem trong trình duyệt ALAD (WebAudio) và xem
   bằng app khác (system capture) — cả hai đều phải khớp hình.
