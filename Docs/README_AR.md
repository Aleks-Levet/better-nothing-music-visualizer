<img src="/Docs/Banners/Banner4.png" alt="مصور موسيقى Nothing المحسن"/> 

🌐 اقرأ هذا بلغات أخرى (غير محدّث): 🇮🇳 [हिन्दी](/Docs/README_HI.md)، 🇮🇳 [Marathi](/Docs/README_MR.md)، 🇹🇷 [Türkçe](/Docs/README_TR.md)

### تطبيق (Android) لا يوفر فقط تصويرًا موسيقيًا أفضل على واجهة Glyph لهواتف Nothing، بل يتيح لك أيضًا الشعور بالموسيقى عبر محرك الاهتزاز، واستخدام كشاف هاتفك كمصور مرئي للموسيقى!

يلتقط تطبيق Android هذا بث الصوت المباشر من جهازك باستخدام مصادر مثل **MediaProjection**، أو **الميكروفون**، أو *المصور المرئي المدمج في Android (غير موصى به)*، أو حتى عبر Shizuku (في المستقبل)، ويعالجه مباشرةً لعرضه باستخدام واجهة Glyph، أو محرك الاهتزاز، أو الكشاف. هذا يعني أنه يمكنك تصوير الموسيقى مرئيًا من **Spotify** و**YouTube Music**، وأي تطبيق آخر تقريبًا! يمكن أن يعمل التطبيق أيضًا مع الألعاب، ويمكنك كذلك استخدام الميكروفون لتصوير الموسيقى التي يشغلها الآخرون، خاصة في الحفلات!

<img 
  src="https://img.shields.io/github/downloads/Aleks-Levet/better-nothing-music-visualizer/total?style=for-the-badge&logo=github&label=Devices%20made%20better:&color=ff0000&labelColor=000000"
  style="height:40px;">

# **إليك زر التنزيل، استخدمه!**
> (إذا كنت لا ترغب في قراءة بقية الملف)

[<img widtht="60%" alt="Get it on GitHub" src="/.github/assets/big-ass-fucking-download-button.png" />](https://github.com/Aleks-Levet/better-nothing-music-visualizer/releases/download/V3.2.1/Better-Nothing-Music-Vizualizer-V3.2.1.apk)
   
## 💬 انضم إلى مجتمعنا على Discord

انقر على الصورة أدناه للانضمام إلى مجتمعنا على Discord والتواصل مع المجتمع:

<a href="https://discord.gg/h7DYNttc8K">
  <img src="https://discord.com/api/guilds/1509496060094054531/widget.png?style=banner3" 
       alt="مجتمع Discord" 
       style="border-radius: 12px; box-shadow: 0 0 15px rgba(88, 101, 242, 0.4); max-width: 100%; height: auto;">
</a>

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Smilies/Thinking%20Face.png" alt="Thinking Face" width="25" height="25" /> لماذا يوجد هذا التطبيق؟
بالنسبة للعديد من الأشخاص (وأنا منهم)، يبدو *مصور الموسيقى الأصلي لـGlyph المقدم من Nothing* عشوائيًا.  
حتى لو لم يكن كذلك من الناحية التقنية، فإن الاستجابة المرئية للموسيقى ليست واضحة جدًا. علاوة على ذلك، لا تستخدم هذه الميزة الإمكانات الكاملة لواجهة Glyph. لذلك قمت ببرمجة المصور المرئي للموسيقى الخاص بي.

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/2696_fe0f/512.gif" alt="⚖" width="32" height="32"> المصور الأصلي مقابل المصور الموسيقي المحسن
| الميزة | Nothing الأصلي | **المصور الموسيقي المحسن** |
| :--- | :--- | :--- |
| **مستويات الإضاءة** | ~2-بت (3 مستويات إضاءة) | **12-بت (4096 مستوى إضاءة)** |
| **معدل التحديث** | 20 Hz *(مقيد بواجهة برمجة المصور في Android)* | **60 Hz** |
| **الدقة** | يبدو عشوائيًا، من الصعب رؤية كيف تتم مزامنته | **يستخدم تحليل FFT لتحديد شدة كل ضوء بدقة** |
| **المناطق** | قياسي، تُستخدم واجهة Glyph المادية بالكامل | **يتم استخدام والتحكم في كل مقطع فرعي ومجال من Glyph بشكل مستقل** |
| **طريقة التصوير المرئي** | الوقت الفعلي فقط | **الوقت الفعلي مع زمن تأخير يصل إلى 20 مللي ثانية، أو ملفات صوتية معالجة مسبقًا** |

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f3ac/512.gif" alt="🎬" width="40" height=""> [عروض فيديو وأمثلة](https://github.com/Aleks-Levet/better-nothing-music-visualizer/blob/main/Docs/Demo-video-examples.md)

### شاهد الفرق بنفسك! [**انقر هنا لمشاهدة عروض الفيديو!**](https://github.com/Aleks-Levet/better-nothing-music-visualizer/blob/main/Docs/Demo-video-examples.md)

## 📲 هواتف Nothing المدعومة لتصوير Glyph المرئي
**هذه الطرازات مدعومة حاليًا:**
- Nothing Phone (1)
- Nothing Phone (2)
- Nothing Phone (2a)
- Nothing Phone (2a plus)
- Nothing Phone (3a)
- Nothing Phone (3a Pro)
- Nothing Phone (3) 
- Nothing Phone (4a)
- Nothing Phone (4b)
- Nothing Phone (4a Pro) *(ليس جاهزًا تمامًا بعد ولكنه على وشك الانتهاء)*

**جميع هواتف Android متوافقة مع استخدام الاهتزاز والكشاف.**


### <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/2699_fe0f/512.gif" alt="⚙" width="25" height="25"> كيف يعمل (تقنيًا)
- **حتمي**: تطبيق BNMV **حتمي** بالكامل. على عكس التنفيذ الأصلي *(أو تطبيقات الطرف الثالث الرديئة المنسوخة بالذكاء الاصطناعي التي تقلد عملنا)* والتي قد تبدو شبه عشوائية، يستخدم المصور المرئي الخاص بنا تحليلًا رياضيًا مباشرًا لبث الصوت لضمان توافق كل ضوء تمامًا مع نطاق تردد معين. هذا يعني أنه *إذا احتفظت بنفس الإعدادات*، فإن **نفس الأغنية ستنتج نفس نمط الإضاءة.**
- يتم التقاط بث صوتي عالي الجودة من خلال المصدر الذي تحدده.
- يُستخدم **FFT** لتحليل الترددات في **نافذة مدتها 20 مللي ثانية** لكل **إطار مدته 16.666 مللي ثانية** (60 Hz)، مما يجعل التصوير المرئي أكثر دقة.
- لتصوير Glyph المرئي:
  - يتم تحديد **نطاقات التردد** لكل منطقة Glyph في ملف `zones.config` وهي قابلة للتخصيص بالكامل.
  - يتم تحديد **سطوع** كل Glyph بواسطة **حجم الذروة** الموجود في نطاق التردد المخصص له.  
  يقيس هذا مدى ارتفاع الصوت في "مناطق" التردد المختلفة.
  - يتم تطبيق **تنعيم هبوطي فقط** لجعل الأنماط المتحركة أكثر سلاسة مع الحفاظ على سرعة الاستجابة (هذا هو السر).
  - بعد ذلك يصبح جاهزًا للعرض على واجهة Glyph!
- لتصوير الاهتزاز والكشاف "المرئي":
  - يتم استخدام شدة تردد الجهير لتحديد سطوع الكشاف أو قوة محرك الاهتزاز.
  - أو يتم استخدام مشتق شدة ترددات الجهير لاكتشاف الإيقاع، مما يؤدي إلى تشغيل نمط معالج مسبقًا إما على محرك الاهتزاز أو الكشاف.

## 🛠️ الإعدادات المسبقة (لتصوير Glyph المرئي)
يتم التحكم في سلوك المصور المرئي بالكامل، بدءًا من نطاقات التردد وحتى تنعيم الأنماط المتحركة، من خلال ملف `zones.config`. سواء كنت ترغب في تعديل الإعدادات المسبقة الحالية أو إضافة دعم لطراز هاتف جديد، يمكنك العثور على كل ما تحتاجه في دليل الإعداد الخاص بنا.
### 📖 [**وثائق zones.config التفصيلية**](/Docs/ZONES_CONFIG.md)

## 📖 كيف تستخدم التطبيق؟
1. **قم بتنزيل أحدث ملف APK** من Releases.
2. **منح الأذونات**: يحتاج التطبيق إلى إذن التقاط الشاشة (MediaProjection) والوصول إلى الإشعارات.
3. **بدء التصوير المرئي**: اضغط على زر "بدء" وقم بتشغيل الموسيقى من أي تطبيق!
4. **ضبط زمن التأخير**: إذا لم تكن الأضواء متزامنة تمامًا مع مكبر صوت Bluetooth أو سماعات الرأس، فاستخدم علامة تبويب **الصوت** لإضافة أو إزالة التأخير.
5. **استمتع!**: استكشف التطبيق وإعداداته المختلفة للاستفادة منه بالكامل!

## 📖 كيف تستخدم نص Python؟ (الطريقة القديمة لمزامنة الموسيقى مع واجهة Glyph في هواتف Nothing)
لقد أنشأنا صفحة Wiki تفصيلية تشرح التثبيت والاستخدام وملفات الإعداد بالتفصيل بالإضافة إلى قسم لاستكشاف الأخطاء وإصلاحها. يمكنك أيضًا معرفة كيفية إنشاء إعدادات مسبقة جديدة (لكن ليس بعد). [فقط انقر هنا لمعرفة كيفية استخدام **musicViz.py** كنص Python](https://github.com/Aleks-Levet/better-nothing-music-visualizer/wiki/). هل تعرف ما هو الرائع؟ يمكنك تحويل عدد غير محدود من الملفات دفعة واحدة دون أي عناء!

## <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Hand%20gestures/Handshake.png" alt="Handshake" width="25" height="25" /> انضم إلى مجتمعنا
هل ترغب في التحدث أو المناقشة؟ *أخطاء، طلبات ميزات؟* 
* [**لا تتردد في الانضمام إلينا في موضوع Discord الرسمي في مجتمع Nothing!**](https://discord.com/channels/930878214237200394/1434923843239280743)
* أو انضم إلى مجتمعنا على Discord! *(انقر أدناه)*

[![Discord Server](https://discord.com/api/guilds/1509496060094054531/widget.png?style=banner3)](https://discord.gg/h7DYNttc8K)


## 🏗️ المساهمة
تعال وساعدنا! المساهمات مرحب بها جدًا!

يمكنك:
* فتح مشكلات (Issues)
* إرسال Pull requests
* اقتراح تحسينات
* تجربة أفكار تصوير مرئي جديدة
* إنشاء إعدادات مسبقة جديدة
* المناقشة مع المطورين

##  <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f512/512.gif" alt="🔒" width="25" height="25"> الخصوصية والأمان
- **الخصوصية**: يلتقط التطبيق فقط بث الصوت لتشغيل المصور المرئي. لا يتم تخزين أو نقل أي محتوى صوتي أو وسائط شخصية على الإطلاق.
- **التحليلات**: يستخدم هذا التطبيق Google Analytics (Firebase) لجمع إحصاءات استخدام مجهولة الهوية وتقارير الأعطال. تساعدنا هذه البيانات على فهم كيفية استخدام التطبيق وإصلاح أي مشكلات تحدث، مما يؤدي في النهاية إلى تحسين التجربة للجميع.

**يمكن العثور على رابط فحص VirusTotal هنا:**  
https://www.virustotal.com/gui/url/c92c1ff82b56eb60bfd1e159592d09f949f0ea2d195e01f7f5adbef0e0b0385b?nocache=1

### <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Symbols/Copyright.png" alt="Copyright" width="25" height="25" /> شكر وتقدير:
#### إليك الأشخاص المشاركون في هذا المشروع:
<table>
  <tr>
    <td>
      <a href="https://github.com/Aleks-Levet">
        <img src="https://github.com/Aleks-Levet.png?size=100&mask=circle" alt="aleks-levet-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Aleks Levet</b></sub>
      </a>
    </td>
    <td>
      <strong>المؤسس والمنسّق وصاحب الفكرة الرئيسية والمالك</strong><br/>
      الفكرة الأساسية وصاحب المشروع. المطور الرئيسي.
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/oliver-lebaigue-bright-bench">
        <img src="https://github.com/oliver-lebaigue-bright-bench.png?size=100&mask=circle" alt="oliver-lebaigue-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Oliver Lebaigue</b></sub>
      </a>
    </td>
    <td>
      <strong>مطوّر تطبيق Android</strong><br/>
      تحسين التطبيق + إضافات رائعة متنوعة.
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/cookiedcdev">
        <img src="https://github.com/cookiedcdev.png?size=100&mask=circle" alt="aleks-levet-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Cookie</b></sub>
      </a>
    </td>
    <td>
      <strong>توافق Phone 3</strong><br/>
      قام بإنشاء إعدادات مسبقة رائعة للمصور المرئي لمصفوفة Glyph الخاصة بـPhone (3)! شكرًا لك!
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/Nicouschulas">
        <img src="https://github.com/Nicouschulas.png?size=100&mask=circle" alt="Nicouschulas-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Nicouschulas</b></sub>
      </a>
    </td>
    <td>
      <strong>الـWiki والوثائق</strong><br/>
      تحسينات ملف Readme والـwiki.
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/SebiAi">
        <img src="https://github.com/SebiAi.png?size=100&mask=circle" alt="sebiai-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>SebiAi</b></sub>
      </a>
    </td>
    <td>
      <strong>أخصائي Glyph</strong><br/>
      تعديل ودعم Glyph.
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/rKyzen">
        <img src="https://github.com/rKyzen.png?size=100&mask=circle" alt="rkyzen-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>rKyzen</b></sub><br/>
        <i>(Shivank Dan)</i>
      </a>
    </td>
    <td>
      <strong>مطور الوظائف الأساسية</strong><br/>
      قام بتنفيذ بث الموسيقى الفوري وبدأ الإصدارات الأولى من تطبيق Android.
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/Earendel-lab">
        <img src="https://github.com/Earendel-lab.png?size=100&mask=circle" alt="earnendel-lab-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Earendel</b></sub>
      </a>
    </td>
    <td>
      <strong>الوثائق</strong><br/>
      تحسينات ملف Readme.
    </td>
  </tr>
  <tr>
    <td>
      <a href="https://github.com/Interlastic">
        <img src="https://github.com/Interlastic.png?size=100&mask=circle" alt="interlastic-pfp" width="50" style="border-radius: 50%; border: 2px solid #555;"><br/>
        <sub><b>Interlastic</b></sub>
      </a>
    </td>
    <td>
      <strong>الأدوات</strong><br/>
      روبوت Discord لاختبار نص الأوامر (لا يعمل حاليًا).
    </td>
  </tr>
</table>

### <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Travel%20and%20places/Star.png" alt="Star" width="25" height="25" />سجل النجوم
<a href="https://www.star-history.com/?repos=Aleks-Levet%2Fbetter-nothing-music-visualizer&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=Aleks-Levet/better-nothing-music-visualizer&type=date&theme=dark&legend=top-left&sealed_token=uvxovJ58naHdu_btCKJ4NCVFxaJ9PE-ZZ6uqvQByXxpP7Vq9oz-siTnlfLNPp6ZFKPcs9Da1hzOKf2q23WK4pA2blP_UD8K9FtE4zD7nOE00sCJjRpGdyMR_83XnsequVLTfgBM5YpxhfbIzOM6SAvcw_qUiedoPTiCzficgf19uj_2PBHA4UmcuHNyI" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=Aleks-Levet/better-nothing-music-visualizer&type=date&legend=top-left&sealed_token=uvxovJ58naHdu_btCKJ4NCVFxaJ9PE-ZZ6uqvQByXxpP7Vq9oz-siTnlfLNPp6ZFKPcs9Da1hzOKf2q23WK4pA2blP_UD8K9FtE4zD7nOE00sCJjRpGdyMR_83XnsequVLTfgBM5YpxhfbIzOM6SAvcw_qUiedoPTiCzficgf19uj_2PBHA4UmcuHNyI" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=Aleks-Levet/better-nothing-music-visualizer&type=date&legend=top-left&sealed_token=uvxovJ58naHdu_btCKJ4NCVFxaJ9PE-ZZ6uqvQByXxpP7Vq9oz-siTnlfLNPp6ZFKPcs9Da1hzOKf2q23WK4pA2blP_UD8K9FtE4zD7nOE00sCJjRpGdyMR_83XnsequVLTfgBM5YpxhfbIzOM6SAvcw_qUiedoPTiCzficgf19uj_2PBHA4UmcuHNyI" />
 </picture>
</a>

# استمتع باستخدام التطبيق!
إذا قرأت ملف Readme بالكامل، فتهانينا، كتابة هذه الأشياء تستغرق وقتًا! إذا وجدت أخطاء مطبعية أو مشاكل أخرى، فلا تتردد في إخبارنا!
