package zzyongx.fsyncer;

import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

public class ImageVideoQuestActivity extends FragmentActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {

  private static final String TAG = ImageVideoQuestActivity.class.getSimpleName();
  private ListView dirListView;
  private SimpleCursorAdapter dirListViewAdapter;

  private String[] IMAGE_PROJECTION = {
          MediaStore.MediaColumns._ID,
          MediaStore.MediaColumns.DATA,
          MediaStore.MediaColumns.DISPLAY_NAME,
  };

  private String[] IMAGE_FROM_COLUMNS = {
          MediaStore.MediaColumns.DATA,
  };

  private int[] IMAGE_TO_FIELDS = {
          R.id.iv_dirName,
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_imagevideoquest);

    dirListViewAdapter = new SimpleCursorAdapter(
            this, R.layout.list_imagevideodir, null,
            IMAGE_FROM_COLUMNS, IMAGE_TO_FIELDS, 0);

    dirListView = (ListView) findViewById(R.id.iv_dirListView);
    dirListView.setAdapter(dirListViewAdapter);
    dirListView.setOnItemClickListener(new ListView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

      }
    });

    getSupportLoaderManager().initLoader(0, null, this);
    // getLoaderManager().initLoader(0, null, this);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new CursorLoader(this, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            IMAGE_PROJECTION, null, null, null);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
    dirListViewAdapter.changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> cursorLoader) {
    dirListViewAdapter.changeCursor(null);
  }
}
