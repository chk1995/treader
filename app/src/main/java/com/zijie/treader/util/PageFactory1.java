package com.zijie.treader.util;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.zijie.treader.Config;
import com.zijie.treader.R;
import com.zijie.treader.ReadActivity;
import com.zijie.treader.db.BookCatalogue;
import com.zijie.treader.view.BookPageWidget;

import org.litepal.crud.DataSupport;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Created by Administrator on 2016/7/20 0020.
 */
public class PageFactory1 {
    private static final String TAG = "PageFactory";
    private static PageFactory1 pageFactory;

    private Context mContext;
    private Config config;
    //当前的书本
    private File book_file = null;
    // 默认背景颜色
    private int m_backColor = 0xffff9e85;
    //页面宽
    private int mWidth;
    //页面高
    private int mHeight;
    //文字字体大小
    private float m_fontSize ;
    //时间格式
    private SimpleDateFormat sdf;
    //时间
    private String date;
    //进度格式
    private DecimalFormat df ;
    //电池边界宽度
    private float mBorderWidth;
    // 上下与边缘的距离
    private float marginHeight ;
    // 左右与边缘的距离
    private float marginWidth ;
    //状态栏距离底部高度
    private float statusMarginBottom;
    //行间距
    private float lineSpace;
    //字体
    private static Typeface typeface;
    //文字画笔
    private Paint mPaint;
    //文字颜色
    private int m_textColor = Color.rgb(50, 65, 78);
    // 绘制内容的宽
    private float mVisibleHeight;
    // 绘制内容的宽
    private float mVisibleWidth;
    // 每页可以显示的行数
    private int mLineCount;
    //电池画笔
    private Paint mBatterryPaint ;
    //背景图片
    private Bitmap m_book_bg = null;
    //当前显示的文字
    private StringBuilder word = new StringBuilder();
    //当前总共的行
    private Vector<String> m_lines = new Vector<>();
    // 当前页起始位置
    private int m_mbBufBegin = 0;
    // 当前页终点位置
    private int m_mbBufEnd = 0;
    // 图书总长度
    private int m_mbBufLen = 0;
    private Intent batteryInfoIntent;
    //电池电量百分比
    private float mBatteryPercentage;
    //电池外边框
    private RectF rect1 = new RectF();
    //电池内边框
    private RectF rect2 = new RectF();
    //文件编码
    private String m_strCharsetName = "GBK";
    // 内存中的图书字符
    private MappedByteBuffer m_mbBuf = null;
    //当前是否为第一页
    private boolean m_isfirstPage;
    //当前是否为最后一页
    private boolean m_islastPage;
    //书本widget
    private BookPageWidget mBookPageWidget;
    private int mstartpos = 0;
    private static List<String> bookCatalogue = new ArrayList<>();
    private static List<Integer> bookCatalogueStartPos = new ArrayList<>();

    public static synchronized PageFactory1 getInstance(){
        return pageFactory;
    }

    public static synchronized PageFactory1 createPageFactory(Context context){
        if (pageFactory == null){
            pageFactory = new PageFactory1(context);
        }
        return pageFactory;
    }

    private PageFactory1(Context context) {
        mContext = context.getApplicationContext();
        config = Config.getInstance();
        //获取屏幕宽高
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metric = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metric);
        mWidth = metric.widthPixels;
        mHeight = metric.heightPixels;

        sdf = new SimpleDateFormat("HH:mm");//HH:mm为24小时制,hh:mm为12小时制
        date = sdf.format(new java.util.Date());
        df = new DecimalFormat("#0.0");

        marginWidth = mContext.getResources().getDimension(R.dimen.readingMarginWidth);
        marginHeight = mContext.getResources().getDimension(R.dimen.readingMarginHeight);
        statusMarginBottom = mContext.getResources().getDimension(R.dimen.reading_status_margin_bottom);
        lineSpace = (int) context.getResources().getDimension(R.dimen.reading_line_spacing);
        mVisibleWidth = mWidth - marginWidth * 2;
        mVisibleHeight = mHeight - marginHeight * 2;

        typeface = config.getTypeface();
        m_fontSize = mContext.getResources().getDimension(R.dimen.reading_default_text_size);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);// 画笔
        mPaint.setTextAlign(Paint.Align.LEFT);// 左对齐
        mPaint.setTextSize(m_fontSize);// 字体大小
        mPaint.setColor(m_textColor);// 字体颜色
        mPaint.setTypeface(typeface);
        mPaint.setSubpixelText(true);// 设置该项为true，将有助于文本在LCD屏幕上的显示效果
        mLineCount = (int) (mVisibleHeight / (m_fontSize + lineSpace));// 可显示的行数

        mBorderWidth = mContext.getResources().getDimension(R.dimen.reading_board_battery_border_width);
        mBatterryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBatterryPaint.setTextSize(CommonUtil.sp2px(context, 12));
        mBatterryPaint.setTypeface(typeface);
        mBatterryPaint.setTextAlign(Paint.Align.LEFT);
        mBatterryPaint.setColor(m_textColor);
        batteryInfoIntent = context.getApplicationContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ;//注册广播,随时获取到电池电量信息

        initBg();
    }

    //初始化背景
    private void initBg(){
        if (config.getDayOrNight()) {
            //设置背景
            setBgBitmap(BookPageFactory.decodeSampledBitmapFromResource(
                    mContext.getResources(), R.drawable.main_bg, mWidth, mHeight));
            //设置字体颜色
            setM_textColor(Color.rgb(128, 128, 128));
        } else {
            Bitmap bmp = Bitmap.createBitmap(mWidth,mHeight, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(mContext.getResources().getColor(R.color.read_background_paperYellow));
            //设置字体颜色
            setM_textColor(mContext.getResources().getColor(R.color.read_textColor));
            //设置背景
            setBgBitmap(bmp);
        }
    }

    public void onDraw(Bitmap bitmap) {
        Canvas c = new Canvas(bitmap);
        c.drawBitmap(getBgBitmap(), 0, 0, null);
        word.setLength(0);
        mPaint.setTextSize(getFontSize());
        mPaint.setColor(getTextColor());
        if (m_lines.size() == 0) {
//            m_lines = pageDown();
            return;
        }

        if (m_lines.size() > 0) {
            float y = marginHeight;
            for (String strLine : m_lines) {
                y += m_fontSize + lineSpace;
                c.drawText(strLine, marginWidth, y, mPaint);
                word.append(strLine);
            }
        }

        //画进度及时间
        int dateWith = (int) (mBatterryPaint.measureText(date)+mBorderWidth);//时间宽度
        float fPercent = (float) (m_mbBufBegin * 1.0 / m_mbBufLen);//进度
        String strPercent = df.format(fPercent * 100) + "%";//进度文字
        int nPercentWidth = (int) mBatterryPaint.measureText("999.9%") + 1;  //Paint.measureText直接返回參數字串所佔用的寬度
        c.drawText(strPercent, mWidth - nPercentWidth, mHeight - statusMarginBottom, mBatterryPaint);//x y为坐标值
        c.drawText(date, marginWidth ,mHeight - statusMarginBottom, mBatterryPaint);
        // 画电池
        int level = batteryInfoIntent.getIntExtra( "level" , 0 );
        int scale = batteryInfoIntent.getIntExtra("scale", 100);
        mBatteryPercentage = (float) level / scale;
        float rect1Left = marginWidth + dateWith + statusMarginBottom;//电池外框left位置
        //画电池外框
        float width = CommonUtil.convertDpToPixel(mContext,20) - mBorderWidth;
        float height = CommonUtil.convertDpToPixel(mContext,10);
        rect1.set(rect1Left, mHeight - height - statusMarginBottom,rect1Left + width, mHeight - statusMarginBottom);
        rect2.set(rect1Left + mBorderWidth, mHeight - height + mBorderWidth - statusMarginBottom, rect1Left + width - mBorderWidth, mHeight - mBorderWidth - statusMarginBottom);
        c.save(Canvas.CLIP_SAVE_FLAG);
        c.clipRect(rect2, Region.Op.DIFFERENCE);
        c.drawRect(rect1, mBatterryPaint);
        c.restore();
        //画电量部分
        rect2.left += mBorderWidth;
        rect2.right -= mBorderWidth;
        rect2.right = rect2.left + rect2.width() * mBatteryPercentage;
        rect2.top += mBorderWidth;
        rect2.bottom -= mBorderWidth;
        c.drawRect(rect2, mBatterryPaint);
        //画电池头
        int poleHeight = (int) CommonUtil.convertDpToPixel(mContext,10) / 2;
        rect2.left = rect1.right;
        rect2.top = rect2.top + poleHeight / 4;
        rect2.right = rect1.right + mBorderWidth;
        rect2.bottom = rect2.bottom - poleHeight/4;
        c.drawRect(rect2, mBatterryPaint);
    }

    /**
     * 向前翻页
     *
     * @throws IOException
     */
    public void prePage() throws IOException {
        if (m_mbBufBegin <= 0) {
            m_mbBufBegin = 0;
            m_isfirstPage = true;
            Toast.makeText(mContext, "当前是第一页", Toast.LENGTH_SHORT).show();
            return;
        } else {
            m_isfirstPage = false;
        }
        onDraw(mBookPageWidget.getCurPage());
        m_lines = pageUp();
        Log.e("prepage",m_lines.toString());
        onDraw(mBookPageWidget.getNextPage());
    }

    /**
     * 向后翻页
     *
     * @throws IOException
     */
    public void nextPage() throws IOException {
        if (m_mbBufEnd >= m_mbBufLen) {
            m_islastPage = true;
            Toast.makeText(mContext, "已经是最后一页了", Toast.LENGTH_SHORT).show();
            return;
        } else {
            m_islastPage = false;
        }
        onDraw(mBookPageWidget.getCurPage());
        m_lines = pageDown();
        Log.e("nextPage",m_lines.toString());
        onDraw(mBookPageWidget.getNextPage());
    }

    /**
     *
     * @param strFilePath
     * @param begin
     *            表示书签记录的位置，读取书签时，将begin值给m_mbBufEnd，在读取nextpage，及成功读取到了书签
     *            记录时将m_mbBufBegin开始位置作为书签记录
     *
     * @throws IOException
     */
    @SuppressWarnings("resource")
    public void openBook(String strFilePath, int begin) throws IOException {
        m_strCharsetName = getCharset(strFilePath);
        if (m_strCharsetName == null){
            m_strCharsetName = "utf-8";
        }

        book_file = new File(strFilePath);
        long lLen = book_file.length();
        m_mbBufLen = (int) lLen;
        m_mbBuf = new RandomAccessFile(book_file, "r").getChannel().map(FileChannel.MapMode.READ_ONLY, 0, lLen);
        if (begin >= 0) {
            m_mbBufBegin = begin;
            m_mbBufEnd = begin;
        } else {
            m_mbBufBegin = 0;
            m_mbBufEnd = 0;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                getBookInfo();
            }
        }).start();

        if (mBookPageWidget != null){
//            pageDown();
//            onDraw(mBookPageWidget.getCurPage());
//            nextPage();
            m_lines = pageDown();
            onDraw(mBookPageWidget.getCurPage());
        }
    }

    /**
     *   提取章节目录及值
     */
    public void getBookInfo() {
        String strParagraph = "";
        while (mstartpos < m_mbBufLen-1) {
            byte[] paraBuf = readParagraphForward(mstartpos);
            mstartpos += paraBuf.length;// 每次读取后，记录结束点位置，该位置是段落结束位置
            try {
                strParagraph = new String(paraBuf, m_strCharsetName);// 转换成制定GBK编码
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "pageDown->转换编码失败", e);
            }
            EditText editText;
            String strReturn = "";
            // 替换掉回车换行符,防止段落发生错乱
            if (strParagraph.indexOf("\r\n") != -1) {   //windows
                strReturn = "\r\n";
                strParagraph = strParagraph.replaceAll("\r\n", "");
            } else if (strParagraph.indexOf("\n") != -1) {    //linux
                strReturn = "\n";
                strParagraph = strParagraph.replaceAll("\n", "");
            }

            if(strParagraph.contains("第") && strParagraph.contains("章")) {
                int m_mstartpos = mstartpos-paraBuf.length;//获得章节段落开始位置
                BookCatalogue bookCatalogue1 = new BookCatalogue();//每次保存后都要新建一个
                strParagraph = strParagraph.trim();//去除字符串前后空格
                //去除全角空格
                while (strParagraph.startsWith("　")) {
                    strParagraph = strParagraph.substring(1, strParagraph.length()).trim();
                }
                bookCatalogue.add(strParagraph);   //保存到数组
                bookCatalogueStartPos.add(m_mstartpos);
                bookCatalogue1.setBookCatalogue(strParagraph);  //保存到数据库
                bookCatalogue1.setBookCatalogueStartPos(m_mstartpos);
                bookCatalogue1.setBookpath(ReadActivity.getBookPath());
                String sql = "SELECT id FROM bookcatalogue WHERE bookcatalogue =? and bookCatalogueStartPos =?";
                Cursor cursor = DataSupport.findBySQL(sql,strParagraph,m_mstartpos +"");
                if(!cursor.moveToFirst()) {
                    bookCatalogue1.save();
                }
            }
        }
    }

    /**
     * 获取文件编码
     * @param fileName
     * @return
     * @throws IOException
     */
    public String getCharset(String fileName) throws IOException{
        String charset;
        FileInputStream fis = new FileInputStream(fileName);
        byte[] buf = new byte[4096];
        // (1)
        UniversalDetector detector = new UniversalDetector(null);
        // (2)
        int nread;
        while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
            detector.handleData(buf, 0, nread);
        }
        // (3)
        detector.dataEnd();
        // (4)
        charset = detector.getDetectedCharset();
        // (5)
        detector.reset();

        return charset;
    }

    /**
     * 画指定页的下一页
     *
     * @return 下一页的内容 Vector<String>
     */
    protected Vector<String> pageDown() {
        m_mbBufBegin = m_mbBufEnd;// 当前页结束位置作为向前翻页的开始位置
        mPaint.setTextSize(m_fontSize);
        mPaint.setColor(m_textColor);

        String strParagraph = "";
        Vector<String> lines = new Vector<>();
        while (lines.size() < mLineCount && m_mbBufEnd < m_mbBufLen) {
            byte[] paraBuf = readParagraphForward(m_mbBufEnd);//获取段落
            m_mbBufEnd += paraBuf.length;// 每次读取后，记录结束点位置，该位置是段落结束位置
            try {
                strParagraph = new String(paraBuf, m_strCharsetName);// 转换成指定编码
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "pageDown->转换编码失败", e);
            }
            String strReturn = "";
            // 替换掉回车换行符,防止段落发生错乱
            if (strParagraph.indexOf("\r\n") != -1) {   //linux
                strReturn = "\r\n";
                strParagraph = strParagraph.replaceAll("\r\n","");
            } else if (strParagraph.indexOf("\n") != -1) {    //windows
                strReturn = "\n";
                strParagraph = strParagraph.replaceAll("\n", "");
            } else if (strParagraph.indexOf("\n") != -1){    //mac
                strReturn = "\r";
                strParagraph.replaceAll("\r","");
            }

            if (strParagraph.length() == 0) {
                lines.add(strParagraph);
            }else {
                while (strParagraph.length() > 0) {
                    // 画一行文字
                    int nSize = mPaint.breakText(strParagraph, true, mVisibleWidth, null);
                    lines.add(strParagraph.substring(0, nSize));
                    strParagraph = strParagraph.substring(nSize);// 得到剩余的文字
                    // 超出最大行数则不再画
                    if (lines.size() >= mLineCount) {
                        break;
                    }
                }

                if (lines.size() < mLineCount) {
                    lines.add("");//段落间加一个空白行
                }

                // 如果该页最后一段只显示了一部分，则重新定位结束点位置
                if (strParagraph.length() != 0) {
                    try {
                        m_mbBufEnd -= (strParagraph + strReturn).getBytes(m_strCharsetName).length;
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "pageDown->记录结束点位置失败", e);
                    }
                }
            }

        }

        return lines;
    }

    /**
     * 得到上上页的结束位置
     */
    protected Vector<String> pageUp() {
        if (m_mbBufBegin < 0) {
            m_mbBufBegin = 0;
        }
        m_mbBufEnd = m_mbBufBegin;// 上上一页的结束点等于上一页的起始点

        Vector<String> lines = new Vector<>();
        String strParagraph = "";
        while (lines.size() < mLineCount && m_mbBufBegin > 0) {
            Vector<String> paraLines = new Vector<>();
            byte[] paraBuf = readParagraphBack(m_mbBufBegin);
            m_mbBufBegin -= paraBuf.length;// 每次读取一段后,记录开始点位置,是段首开始的位置
            try {
                strParagraph = new String(paraBuf, m_strCharsetName);
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "pageUp->转换编码失败", e);
            }
            String strReturn = "";
            // 替换掉回车换行符,防止段落发生错乱
            if (strParagraph.indexOf("\r\n") != -1) {   //linux
                strReturn = "\r\n";
                strParagraph = strParagraph.replaceAll("\r\n","");
            } else if (strParagraph.indexOf("\n") != -1) {    //windows
                strReturn = "\n";
                strParagraph = strParagraph.replaceAll("\n", "");
            } else if (strParagraph.indexOf("\n") != -1){    //mac
                strReturn = "\r";
                strParagraph.replaceAll("\r","");
            }

            // 如果是空白行，直接添加
            if (strParagraph.length() == 0) {
                lines.add(0,strParagraph);
            }else {
                while (strParagraph.length() > 0) {
                    // 画一行文字
                    int nSize = mPaint.breakText(strParagraph, true, mVisibleWidth,
                            null);
                    paraLines.add(strParagraph.substring(0, nSize));
                    strParagraph = strParagraph.substring(nSize);
                }

                //这是一个段落的结尾，如果段落的结尾在页面的结尾不加空行
                if (!strReturn.isEmpty() && !lines.isEmpty()) {
                    paraLines.add("");
                }

                for (int i = paraLines.size() - 1; i >= 0 ;i--){
                    if (lines.size() < mLineCount) {
                        lines.add(0, paraLines.get(i));
                    }else{
                        try {
                            m_mbBufBegin += paraLines.get(i).getBytes(m_strCharsetName).length;
                        } catch (UnsupportedEncodingException e) {
                            Log.e(TAG, "pageUp->记录起始点位置失败", e);
                        }
                    }
                }
            }
        }
        return lines;
    }

    /**
     * 读取指定位置的下一个段落
     *
     * @param nFromPos
     * @return byte[]
     */
    protected byte[] readParagraphForward(int nFromPos) {
        int nStart = nFromPos;
        int i = nStart;
        byte b0, b1;
        // 根据编码格式判断换行
        if (m_strCharsetName.equals("UTF-16LE")) {
            while (i < m_mbBufLen - 1) {
                b0 = m_mbBuf.get(i++);
                b1 = m_mbBuf.get(i++);
                if (b0 == 0x0a && b1 == 0x00) {
                    break;
                }
            }
        } else if (m_strCharsetName.equals("UTF-16BE")) {
            while (i < m_mbBufLen - 1) {
                b0 = m_mbBuf.get(i++);
                b1 = m_mbBuf.get(i++);
                if (b0 == 0x00 && b1 == 0x0a) {
                    break;
                }
            }
        } else {
            while (i < m_mbBufLen) {
                b0 = m_mbBuf.get(i++);
                if (b0 == 0x0a) {
                    break;
                }
            }
        }
        int nParaSize = i - nStart; //段落长度
        byte[] buf = new byte[nParaSize];
        for (i = 0; i < nParaSize; i++) {
            buf[i] = m_mbBuf.get(nFromPos + i);
        }
        return buf;
    }

    /**
     * 读取指定位置的上一个段落
     *
     * @param nFromPos
     * @return byte[]
     */
    protected byte[] readParagraphBack(int nFromPos) {
        int nEnd = nFromPos;
        int i;
        byte b0, b1;
        if (m_strCharsetName.equals("UTF-16LE")) {
            i = nEnd - 2;
            while (i > 0) {
                b0 = m_mbBuf.get(i);
                b1 = m_mbBuf.get(i + 1);
                if (b0 == 0x0a && b1 == 0x00 && i != nEnd - 2) {
                    i += 2;
                    break;
                }
                i--;
            }

        } else if (m_strCharsetName.equals("UTF-16BE")) {
            i = nEnd - 2;
            while (i > 0) {
                b0 = m_mbBuf.get(i);
                b1 = m_mbBuf.get(i + 1);
                if (b0 == 0x00 && b1 == 0x0a && i != nEnd - 2) {
                    i += 2;
                    break;
                }
                i--;
            }
        } else {
            i = nEnd - 1;
            while (i > 0) {
                b0 = m_mbBuf.get(i);
                if (b0 == 0x0a && i != nEnd - 1) {// 0x0a表示换行符
                    i++;
                    break;
                }
                i--;
            }
        }
        if (i < 0)
            i = 0;
        int nParaSize = nEnd - i;
        int j;
        byte[] buf = new byte[nParaSize];
        for (j = 0; j < nParaSize; j++) {
            buf[j] = m_mbBuf.get(i + j);
        }
        return buf;
    }


    public boolean isfirstPage() {
        return m_isfirstPage;
    }

    public boolean islastPage() {
        return m_islastPage;
    }
    //设置页面背景
    public void setBgBitmap(Bitmap BG) {
        m_book_bg = BG;
    }
    //设置页面背景
    public Bitmap getBgBitmap() {
        return m_book_bg;
    }
    //设置文字颜色
    public void setM_textColor(int m_textColor) {
        this.m_textColor = m_textColor;
    }
    //获取文字颜色
    public int getTextColor() {
        return this.m_textColor;
    }
    //获取文字大小
    public float getFontSize() {
        return this.m_fontSize;
    }

    public void setPageWidget(BookPageWidget mBookPageWidget){
        this.mBookPageWidget = mBookPageWidget;
    }

}
