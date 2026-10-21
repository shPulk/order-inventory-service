package com.acme.oims.catalog;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository products;

    public ProductController(ProductRepository products) {
        this.products = products;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ProductView> create(@Valid @RequestBody CreateProductRequest request) {
        Product saved = products.save(new Product(request.sku(), request.name(), request.price()));
        return ResponseEntity.created(URI.create("/api/products/" + saved.getId()))
                .body(ProductView.from(saved));
    }

    @GetMapping
    public List<ProductView> list() {
        return products.findAll().stream().map(ProductView::from).toList();
    }
}
