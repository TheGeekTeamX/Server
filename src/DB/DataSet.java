package DB;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

@Entity(name = "DataSets")

public class DataSet implements IDBEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name="Id")
	private int id;
    @OneToOne(targetEntity = User.class)
    @JoinColumn(name = "UserId")
    private User user;
	@Column(name="LengthOfRecorcds")
	private float lengthOfRecorcds;
	

	
	
	public int getId() {
		return id;
	}




	public void setId(int id) {
		this.id = id;
	}




	public User getUser() {
		return user;
	}




	public void setUser(User user) {
		this.user = user;
	}




	public float getLengthOfRecorcds() {
		return lengthOfRecorcds;
	}




	public void setLengthOfRecorcds(float lengthOfRecorcds) {
		this.lengthOfRecorcds = lengthOfRecorcds;
	}




	public DataSet(User user, float lengthOfRecorcds) {
		super();
		this.user = user;
		this.lengthOfRecorcds = lengthOfRecorcds;
	}




	@Override
	public void update(IDBEntity other) {
		// TODO Auto-generated method stub
		if(other.getClass() == this.getClass())
		{			
			this.id = ((DataSet)other).id;
			this.user = ((DataSet)other).user;
			this.lengthOfRecorcds = ((DataSet)other).lengthOfRecorcds;
		}

	}

}
