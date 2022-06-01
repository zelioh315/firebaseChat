package uk.ac.wlv.firebasechat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.firebase.ui.database.SnapshotParser;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

public class MainActivity extends AppCompatActivity  implements GoogleApiClient.OnConnectionFailedListener{
    private static final String TAG = "MainActivity";
    public static final String MESSAGES_CHILD = "messages";
    private static final int REQUEST_INVITE = 1;
    private static final int REQUEST_IMAGE = 2;
    private static final String LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif";
    private static final int DEFAULT_MSG_LENGTH_LIMIT = 10;
    public static final String ANONYMOUS = "anonymous";
    private static final String MESSAGE_SENT_EVENT = "message_sent";
    private String mUsername;
    private String mPhotoUrl;
    private SharedPreferences mSharedPreferences;
    private GoogleApiClient mGoogleApiClient;
    private Button mSendButton;
    private RecyclerView mMessageRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private ProgressBar mProgressBar;
    private EditText mMessageEditText;
    private ImageView mAddMessageImageView;



    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;

    private DatabaseReference mFirebaseDatabaseReference;
    private FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder> mFirebaseAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mUsername = ANONYMOUS;

        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        if (mFirebaseUser==null){
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        }else {
            mUsername=mFirebaseUser.getDisplayName();
            if(mFirebaseUser.getPhotoUrl() !=null){
                mPhotoUrl=mFirebaseUser.getPhotoUrl().toString();
            }
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this).enableAutoManage(this, this).addApi(Auth.GOOGLE_SIGN_IN_API).build();

        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageRecyclerView=(RecyclerView)findViewById(R.id.messageRecyclerView);
        mLinearLayoutManager=new LinearLayoutManager(this);
        mLinearLayoutManager.setStackFromEnd(true);
        mMessageRecyclerView.setLayoutManager(mLinearLayoutManager);
      //  mProgressBar.setVisibility(ProgressBar.INVISIBLE);
        loadFirebaseMessages();

        mMessageEditText=(EditText) findViewById(R.id.messageEditText);
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().length()>0){
                    mSendButton.setEnabled(true);
                }else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        mSendButton=(Button) findViewById(R.id.sendButton);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChatMessage chatMessage = new ChatMessage(mMessageEditText.getText().toString(), mUsername, mPhotoUrl,null);
                mFirebaseDatabaseReference.child(MESSAGES_CHILD).push().setValue(chatMessage);
                mMessageEditText.setText("");

            }
        });

        mAddMessageImageView = (ImageView) findViewById(R.id.addMessageImageView);
        mAddMessageImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TEXT,mMessageEditText.getText().toString());
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_IMAGE);

            }
        });
    }
    @Override
    public void onStart(){
        super.onStart();
        //  Log.d(TAG,"onStart() called");
    }
    @Override
    public void onPause(){
        mFirebaseAdapter.stopListening();
        super.onPause();
        //  Log.d(TAG, "onPause() called");
    }
    @Override
    public void onResume(){
        super.onResume();
        mFirebaseAdapter.startListening();
        //  Log.d(TAG,"onResume() called");
    }

    @Override
    public void onStop(){
        super.onStop();
        //  Log.d(TAG, "onStop() called");
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        //  Log.d(TAG,"onDestroy() called");
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case R.id.sign_out_menu:
                mFirebaseAuth.signOut();
                Auth.GoogleSignInApi.signOut(mGoogleApiClient);
                mUsername = ANONYMOUS;
                startActivity(new Intent(this, SignInActivity.class));
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG,"onActivityResult: requestCode ="+requestCode+", resultCode ="+resultCode);
        if(requestCode==REQUEST_IMAGE){
            if(resultCode==RESULT_OK){
                if(data!=null){

                    final Uri uri = data.getData();
                    Log.d(TAG,"Uri: "+uri.toString());

                    ChatMessage    tempMessage = new ChatMessage(mMessageEditText.getText().toString(), mUsername, mPhotoUrl, LOADING_IMAGE_URL);

                    mFirebaseDatabaseReference.child(MESSAGES_CHILD).push().setValue(tempMessage,
                            new DatabaseReference.CompletionListener() {
                                @Override
                                public void onComplete(DatabaseError error, @NonNull DatabaseReference ref) {
                                    if(error== null){
                                        String key= ref.getKey();
                                        StorageReference storageReference= FirebaseStorage.getInstance().getReference(mFirebaseUser.getUid())
                                                .child(key).child(uri.getLastPathSegment());
                                        putImageInStorage(storageReference,uri,key);


                                    }else{
                                        Log.v(TAG,"Unable to write message to database.",error.toException());
                                    }
                                }
                            });
                    //mMessageEditText.setText("");
                }
            }
        }
    }
    private void putImageInStorage(StorageReference storageReference,Uri uri,final String key){
        storageReference.putFile(uri).addOnCompleteListener(MainActivity.this,
                new OnCompleteListener<UploadTask.TaskSnapshot>(){
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        if(task.isSuccessful()){
                            task.getResult().getMetadata().getReference().getDownloadUrl().addOnCompleteListener(
                                    MainActivity.this, new OnCompleteListener<Uri>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Uri> task) {
                                            if(task.isSuccessful()){
                                                ChatMessage friendlyMessage= new ChatMessage(mMessageEditText.getText().toString(),mUsername,mPhotoUrl,task.getResult().toString());
                                                mFirebaseDatabaseReference.child(MESSAGES_CHILD).child(key).setValue(friendlyMessage);
                                                mMessageEditText.setText("");
                                            }
                                        }
                                    });

                        }else {
                            Log.v(TAG,"Image upload task was not successful.",task.getException());
                        }
                    }
                });
    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }
    private void loadFirebaseMessages() {
        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
        SnapshotParser<ChatMessage> parser = new SnapshotParser<ChatMessage>() {

            public ChatMessage parseSnapshot(DataSnapshot dataSnapshot) {
                ChatMessage ChatMessage = dataSnapshot.getValue(ChatMessage.class);
                if (ChatMessage != null)
                    ChatMessage.setId(dataSnapshot.getKey());
                return ChatMessage;
            }
        };
        DatabaseReference messageRef = mFirebaseDatabaseReference.child(MESSAGES_CHILD);
        FirebaseRecyclerOptions<ChatMessage> options =
                new FirebaseRecyclerOptions.Builder<ChatMessage>().setQuery(messageRef, parser).build();
        mFirebaseAdapter = new FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder>(options) {
            @Override
            public MessageViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
                LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
                return new MessageViewHolder(inflater.inflate(R.layout.item_message, viewGroup, false));
            }

            @Override
            protected void onBindViewHolder(final MessageViewHolder viewHolder, int position, ChatMessage ChatMessage) {
                mProgressBar.setVisibility(ProgressBar.INVISIBLE);
                if (ChatMessage.getText() != null && ChatMessage.getImageUrl()== null ) {
                    viewHolder.messageTextView.setText(ChatMessage.getText());
                    viewHolder.messageTextView.setVisibility(TextView.VISIBLE);
                    viewHolder.messageImageView.setVisibility(ImageView.GONE);
                } else if (ChatMessage.getImageUrl() != null && ChatMessage.getText() == null) {
                    String imageUrl = ChatMessage.getImageUrl();
                    if (imageUrl.startsWith("gs://")) {
                        StorageReference storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl);
                        storageReference.getDownloadUrl().addOnCompleteListener(
                                new OnCompleteListener<Uri>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Uri> task) {
                                        if (task.isSuccessful()) {
                                            String downloadUrl = task.getResult().toString();
                                            Glide.with(viewHolder.messageImageView.getContext())
                                                    .load(downloadUrl).into(viewHolder.messageImageView);
                                        } else {
                                            Log.v(TAG, "Getting download url was not successful.", task.getException());
                                        }
                                    }
                                });

                    } else {
                        Glide.with(viewHolder.messageImageView.getContext())
                                .load(ChatMessage.getImageUrl()).into(viewHolder.messageImageView);
                    }
                    viewHolder.messageImageView.setVisibility(ImageView.VISIBLE);
                    viewHolder.messageTextView.setVisibility(TextView.GONE);
                }else  if (ChatMessage.getText()!=null && ChatMessage.getImageUrl()!=null){
                    viewHolder.messageTextView.setText(ChatMessage.getText());
                    String imageUrl = ChatMessage.getImageUrl();
                    if (imageUrl.startsWith("gs://")) {
                        StorageReference storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl);
                        storageReference.getDownloadUrl().addOnCompleteListener(
                                new OnCompleteListener<Uri>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Uri> task) {
                                        if (task.isSuccessful()) {
                                            String downloadUrl = task.getResult().toString();

                                            Glide.with(viewHolder.messageImageView.getContext())
                                                    .load(downloadUrl).into(viewHolder.messageImageView);
                                        } else {
                                            Log.v(TAG, "Getting download url was not successful.", task.getException());
                                        }
                                    }
                                });

                    } else {
                        viewHolder.messageTextView.setText(ChatMessage.getText());
                        Glide.with(viewHolder.messageImageView.getContext())
                                .load(ChatMessage.getImageUrl()).into(viewHolder.messageImageView);

                    }
                    viewHolder.messageImageView.setVisibility(ImageView.VISIBLE);
                    viewHolder.messageTextView.setVisibility(TextView.VISIBLE);
                    // viewHolder.messageTextView.setVisibility(TextView.GONE);
                }
                viewHolder.messengerTextView.setText(ChatMessage.getName());
                if (ChatMessage.getPhotoUrl() != null) {
                    viewHolder.messageTextView.setText(ChatMessage.getText());
                    Glide.with(MainActivity.this).load(ChatMessage.getPhotoUrl()).into(viewHolder.messengerImageView);
                }

                viewHolder.messageTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        viewHolder.mDeleteImageButton.setEnabled(true);

                    }
                });

                viewHolder.messageImageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        viewHolder.mDeleteImageButton.setEnabled(true);

                    }
                });



                viewHolder.mDeleteImageButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        Query DeleteQuery;
                        Log.i("MessageRemove", ChatMessage.getText());
                        Log.i("removeMessage","Inside Method");
                        if(ChatMessage.getText()!=null) {
                            DeleteQuery = mFirebaseDatabaseReference.child(MESSAGES_CHILD).orderByChild("text").equalTo(ChatMessage.getText());
                        }else{
                            DeleteQuery = mFirebaseDatabaseReference.child(MESSAGES_CHILD).orderByChild("imageUrl").equalTo(ChatMessage.getImageUrl());

                        }
                        DeleteQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                for (DataSnapshot DeleteSnapshot: dataSnapshot.getChildren()) {
                                    Log.i("snapshot", DeleteSnapshot.toString());
                                    Log.i("snapshotRef", DeleteSnapshot.getRef().toString());
                                    String key = DeleteSnapshot.getKey();

                                    DeleteSnapshot.getRef().removeValue();
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                                Log.e("TAG", "onCancelled", databaseError.toException());
                            }
                        });


                    }
                });


            }
        };
        mFirebaseAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int ChatMessageCount = mFirebaseAdapter.getItemCount();
                int lastVisiblePosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
                if (lastVisiblePosition == -1 || (positionStart >= (ChatMessageCount - 1) && lastVisiblePosition == (positionStart - 1))) {
                    mMessageRecyclerView.scrollToPosition(positionStart);
                }
            }
        });
        mMessageRecyclerView.setAdapter(mFirebaseAdapter);

    }


    public static class MessageViewHolder extends RecyclerView.ViewHolder{
        TextView messageTextView;
        ImageView messageImageView;
        TextView messengerTextView;
        ImageView messengerImageView;
        Button mDeleteImageButton;

        public MessageViewHolder(View v){
            super(v);
            messageTextView = (TextView) itemView.findViewById(R.id.messageTextView);
            messageImageView = (ImageView) itemView.findViewById(R.id.messageImageView);
            messengerTextView = (TextView) itemView.findViewById(R.id.messengerTextView);
            messengerImageView = (ImageView) itemView.findViewById(R.id.messengerImageView);
            mDeleteImageButton = (Button) itemView.findViewById(R.id.deleteButton);
        }

    }


}

