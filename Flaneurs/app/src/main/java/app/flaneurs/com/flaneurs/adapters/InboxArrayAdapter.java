package app.flaneurs.com.flaneurs.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.parse.ParseException;

import org.ocpsoft.prettytime.PrettyTime;

import java.util.List;

import app.flaneurs.com.flaneurs.R;
import app.flaneurs.com.flaneurs.models.InboxItem;
import app.flaneurs.com.flaneurs.models.Post;
import app.flaneurs.com.flaneurs.models.User;
import app.flaneurs.com.flaneurs.utils.Utils;
import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by kamranpirwani on 3/5/16.
 */
public class InboxArrayAdapter extends RecyclerView.Adapter<InboxArrayAdapter.InboxViewHolder> {
    private List<InboxItem> mFlans;
    private IInboxInteractionListener mListener;
    private Context mContext;

    public InboxArrayAdapter(Context context, List<InboxItem> flans, IInboxInteractionListener listener) {
        mFlans = flans;
        mListener = listener;
        mContext = context;
    }

    @Override
    public InboxViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflator = LayoutInflater.from(parent.getContext());
        View view = inflator.inflate(R.layout.flan_inbox_item, parent, false);
        InboxViewHolder viewHolder = new InboxViewHolder(view, new InboxViewHolder.IMyViewHolderClicks() {
            @Override
            public void onInboxClicked(View caller, int position) {
                InboxItem flan = mFlans.get(position);
                mListener.openInboxDetailView(flan);
            }
        });
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(InboxViewHolder holder, final int position) {
        final InboxItem flan1 = mFlans.get(position);
        final Post flan = flan1.getPost();

        User author = flan.getAuthor();
        if (author != null) {
            try {
                author.fetch();
            } catch (ParseException e) {
                e.printStackTrace();
            }
            String username = author.getUsername();
            holder.tvUsername.setText(username);
        }

        PrettyTime pt = new PrettyTime();
        holder.tvCreationTime.setText(pt.format(flan.getCreatedTime()));
        if (flan.getLocation() != null) {
            String address = Utils.getPrettyAddress(mContext, flan.getLocation().getLatitude(), flan.getLocation().getLongitude());
            if (address != null) {
                holder.tvLocation.setText(address);
            }
            if (author.getProfileUrl() != null) {
                Glide.with(mContext).load(author.getProfileUrl()).into(holder.ivInboxImage);
            }
        }

        if (flan1.getNew() == true) {
            holder.ivNew.setVisibility(View.VISIBLE);
        } else {
            holder.ivNew.setVisibility(View.GONE);
        }

    }

    @Override
    public int getItemCount() {
        return mFlans.size();
    }

    public static class InboxViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        @Bind(R.id.ivInboxImage)
        ImageView ivInboxImage;

        @Bind(R.id.ivNew)
        ImageView ivNew;

        @Bind(R.id.tvInboxUsername)
        TextView tvUsername;

        @Bind(R.id.tvInboxCreationTime)
        TextView tvCreationTime;

        @Bind(R.id.tvInboxLocation)
        TextView tvLocation;

        IMyViewHolderClicks mListener;

        public InboxViewHolder(View itemView, IMyViewHolderClicks listener) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            itemView.setOnClickListener(this);
            mListener = listener;
        }

        @Override
        public void onClick(View v) {
            mListener.onInboxClicked(v, getAdapterPosition());
            ivNew.setVisibility(View.GONE);
        }

        public interface IMyViewHolderClicks {
            void onInboxClicked(View caller, int position);
        }
    }

    public interface IInboxInteractionListener {
        void openInboxDetailView(InboxItem flan);
    }

}
