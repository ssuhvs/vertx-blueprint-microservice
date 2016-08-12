package io.vertx.blueprint.microservice.product.impl;

import io.vertx.blueprint.microservice.product.Product;
import io.vertx.blueprint.microservice.product.ProductService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JDBC implementation of {@link io.vertx.blueprint.microservice.product.ProductService}.
 *
 * @author Eric Zhao
 */
public class ProductServiceImpl implements ProductService {

  private static final int PAGE_LIMIT = 10;

  private static final String CREATE_STATEMENT = "CREATE TABLE IF NOT EXISTS `product` (\n" +
    "  `productId` VARCHAR(60) NOT NULL,\n" +
    "  `sellerId` varchar(30) NOT NULL,\n" +
    "  `name` varchar(255) NOT NULL,\n" +
    "  `price` double NOT NULL,\n" +
    "  `illustration` varchar(255) NOT NULL,\n" +
    "  `type` varchar(45) NOT NULL,\n" +
    "  PRIMARY KEY (`productId`),\n" +
    "  KEY `index_seller` (`sellerId`) )";
  private static final String INSERT_STATEMENT = "INSERT INTO product (productId, sellerId, name, price, illustration, type) VALUES (?, ?, ?, ?, ?, ?)";
  private static final String FETCH_STATEMENT = "SELECT * FROM product WHERE productId = ?";
  private static final String FETCH_ALL_STATEMENT = "SELECT * FROM product";
  private static final String FETCH_WITH_PAGE_STATEMENT = "SELECT * FROM product LIMIT ?, ?";
  private static final String DELETE_STATEMENT = "DELETE FROM product WHERE productId = ?";
  private static final String DELETE_ALL_STATEMENT = "DELETE FROM product";

  private final JDBCClient jdbc;

  public ProductServiceImpl(Vertx vertx, JsonObject config) {
    this.jdbc = JDBCClient.createNonShared(vertx, config);
  }

  @Override
  public ProductService initializePersistence(Handler<AsyncResult<Void>> resultHandler) {
    jdbc.getConnection(connHandler(resultHandler, connection -> {
      connection.execute(CREATE_STATEMENT, r -> {
        resultHandler.handle(r);
        if (r.failed())
          r.cause().printStackTrace();
        connection.close();
      });
    }));
    return this;
  }

  @Override
  public ProductService addProduct(Product product, Handler<AsyncResult<Void>> resultHandler) {
    jdbc.getConnection(connHandler(resultHandler, connection -> {
      connection.updateWithParams(INSERT_STATEMENT, new JsonArray().add(product.getProductId())
        .add(product.getSellerId())
        .add(product.getName())
        .add(product.getPrice())
        .add(product.getIllustration())
        .add(product.getType()), r -> {
        if (r.succeeded()) {
          resultHandler.handle(Future.succeededFuture());
        } else {
          resultHandler.handle(Future.failedFuture(r.cause()));
        }
        connection.close();
      });
    }));
    return this;
  }

  @Override
  public ProductService retrieveProduct(String productId, Handler<AsyncResult<Product>> resultHandler) {
    jdbc.getConnection(connHandler(resultHandler, connection -> {
      connection.queryWithParams(FETCH_STATEMENT, new JsonArray().add(productId), r -> {
        if (r.succeeded()) {
          List<JsonObject> resList = r.result().getRows();
          if (resList == null || resList.isEmpty()) {
            resultHandler.handle(Future.succeededFuture());
          } else {
            resultHandler.handle(Future.succeededFuture(new Product(resList.get(0))));
          }
        } else {
          resultHandler.handle(Future.failedFuture(r.cause()));
        }
        connection.close();
      });
    }));
    return this;
  }

  @Override
  public ProductService retrieveProductPrice(String productId, Handler<AsyncResult<JsonObject>> resultHandler) {
    retrieveOneNonModify("SELECT price FROM product WHERE productId = ?", productId, resultHandler);
    return this;
  }

  @Override
  public ProductService retrieveProductsByPage(int page, Handler<AsyncResult<List<Product>>> resultHandler) {
    jdbc.getConnection(connHandler(resultHandler, connection -> {
      connection.queryWithParams(FETCH_WITH_PAGE_STATEMENT,
        new JsonArray().add(calcPage(page)).add(PAGE_LIMIT),
        r -> {
          if (r.succeeded()) {
            List<Product> resList = r.result().getRows().stream()
              .map(Product::new)
              .collect(Collectors.toList());
            resultHandler.handle(Future.succeededFuture(resList));
          } else {
            resultHandler.handle(Future.failedFuture(r.cause()));
          }
          connection.close();
        });
    }));
    return this;
  }

  @Override
  public ProductService retrieveAllProducts(Handler<AsyncResult<List<Product>>> resultHandler) {
    jdbc.getConnection(connHandler(resultHandler, connection -> {
      connection.query(FETCH_ALL_STATEMENT, r -> {
        if (r.succeeded()) {
          List<Product> resList = r.result().getRows().stream()
            .map(Product::new)
            .collect(Collectors.toList());
          resultHandler.handle(Future.succeededFuture(resList));
        } else {
          resultHandler.handle(Future.failedFuture(r.cause()));
        }
        connection.close();
      });
    }));
    return this;
  }

  @Override
  public ProductService deleteProduct(String productId, Handler<AsyncResult<Void>> resultHandler) {
    jdbc.getConnection(connHandler(resultHandler, connection -> {
      JsonArray params = new JsonArray().add(productId);
      connection.updateWithParams(DELETE_STATEMENT, params, r -> {
        if (r.succeeded()) {
          resultHandler.handle(Future.succeededFuture());
        } else {
          resultHandler.handle(Future.failedFuture(r.cause()));
        }
        connection.close();
      });
    }));
    return this;
  }

  @Override
  public ProductService deleteAllProducts(Handler<AsyncResult<Void>> resultHandler) {
    jdbc.getConnection(connHandler(resultHandler, connection -> {
      connection.update(DELETE_ALL_STATEMENT, r -> {
        if (r.succeeded()) {
          resultHandler.handle(Future.succeededFuture());
        } else {
          resultHandler.handle(Future.failedFuture(r.cause()));
        }
        connection.close();
      });
    }));
    return this;
  }

  private int calcPage(int page) {
    if (page <= 0)
      return 0;
    return PAGE_LIMIT * (page - 1);
  }

  /**
   * A helper methods that generates async handler for SQLConnection
   *
   * @return generated handler
   */
  private <T> Handler<AsyncResult<SQLConnection>> connHandler(Handler<AsyncResult<T>> h1, Handler<SQLConnection> h2) {
    return conn -> {
      if (conn.succeeded()) {
        final SQLConnection connection = conn.result();
        h2.handle(connection);
      } else {
        h1.handle(Future.failedFuture(conn.cause()));
      }
    };
  }

  private void retrieveOneNonModify(String sql, String productId, Handler<AsyncResult<JsonObject>> resultHandler) {
    jdbc.getConnection(connHandler(resultHandler, connection -> {
      // query product price
      connection.queryWithParams(sql, new JsonArray().add(productId),
        r -> {
          if (r.succeeded()) {
            List<JsonObject> resList = r.result().getRows();
            if (resList == null || resList.isEmpty()) {
              resultHandler.handle(Future.succeededFuture());
            } else {
              resultHandler.handle(Future.succeededFuture(resList.get(0)));
            }
          } else {
            resultHandler.handle(Future.failedFuture(r.cause()));
          }
          connection.close();
        });
    }));
  }
}