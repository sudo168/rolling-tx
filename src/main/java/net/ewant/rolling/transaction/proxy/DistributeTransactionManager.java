package net.ewant.rolling.transaction.proxy;

import net.ewant.rolling.transaction.TransactionContext;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;

import javax.sql.DataSource;

@Component
public class DistributeTransactionManager extends DataSourceTransactionManager {

	private static final long serialVersionUID = 3340691153214637378L;

	public DistributeTransactionManager(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	protected void doRollback(DefaultTransactionStatus status) {
		super.doRollback(status);
		TransactionContext.getContext().markRollback();
	}
}
