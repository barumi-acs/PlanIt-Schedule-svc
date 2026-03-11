package com.planit.category.category_list;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.planit.global.BaseTimeEntity;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "category_list")
@SQLDelete(sql = "UPDATE category_list SET deleted_at = CURRENT_TIMESTAMP(6) WHERE list_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class CategoryList extends BaseTimeEntity {

    /**
     * PK: category_list.list_id
     * - User-svc의 category_id와 동일한 값으로 동기화됨
     */
    @Id
    @Column(name = "list_id")
    private Long listId;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(columnDefinition = "TEXT")
    private String description;
}
