package zzyongx.fsyncer;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class WelcomeActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_welcome);

    Button button = (Button) findViewById(R.id.welcome_startButton);
    button.setOnClickListener(new View.OnClickListener() {
        public void onClick(View view) {
          finish();
        }
      });

    
  }
}
